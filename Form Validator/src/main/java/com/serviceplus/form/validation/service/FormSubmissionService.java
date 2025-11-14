package com.serviceplus.form.validation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.Services;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;
import com.serviceplus.form.validation.utility.ApplicationFlowDecider;
import com.serviceplus.form.validation.utility.Utility;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.serviceplus.form.validation.utility.ApplicationConstants.OFFICIAL_TASK_FLAG;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

@Service
@SanitizeRequest
public class FormSubmissionService {

    @Autowired
    private ReactiveApiClient reactiveApiClient;

    @Autowired
    private ProcessingTxnRepository txnRepository;

    @Autowired
    private ApplicationGenerationService applicationGenerationService;

    @Autowired
    private PreProcessingFacade preProcessingFacade;

    @Autowired
    private TempTransactionLogService tempTransactionLogService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationFlowDecider applicationFlowDecider;

    private static final Logger log = LogManager.getLogger("FormSubmissionLogger");

    public Mono<ServerResponse> applicationSubmission(ServerHttpRequest request, String txnId, String appData,
                                                      String applyKey, String appId, boolean draft, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus) {
        try {
            UserSessionObject user = getUserSessionDetails(request);

            Services service = preProcessingFacade.decryptApplyKey(applyKey);

            if(appId.isEmpty() && service.getTaskType().equals(OFFICIAL_TASK_FLAG)){
                return Mono.error(new SPRuntimeError(
                        "Issue while processing the request [SUB - 008]",
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));
            }

            return saveFormData(txnId, user,appData,service,appId,draft,request,reactiveRequestObject,flowStatus);

        } catch (Exception e) {
            e.printStackTrace();
            return Mono.error(new SPRuntimeError("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }


    public Mono<ServerResponse> saveFormData(
            String txnId,
            UserSessionObject user,
            String appData,
            Services service,
            String appId,
            boolean draft,
            ServerHttpRequest request, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus) {

        return tempTransactionLogService.fetch(txnId)
                .switchIfEmpty(
                        createTempAndExecute(txnId,user,appData,service,appId,draft,request,reactiveRequestObject, flowStatus,new TempTransactionLogs())
                 )
                 .flatMap(
                         txnLog -> executeTransaction(txnId,user,appData,service,appId,draft,request,reactiveRequestObject, flowStatus,txnLog)
                )
                .flatMap(result ->
                        ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(result)
                )
                .onErrorResume(Exception.class, ex -> {
                    Throwable actual = Exceptions.unwrap(ex);
                    if (actual instanceof SPRuntimeError spr) {
                        log.warn("Error while saving form data: {}", spr.getMessage());
                        return Mono.error(new SPRuntimeError(spr.getMessage(), spr.getErrorCode()));
                    }
                    log.error("Unexpected error while saving form data", ex);
                    return Mono.error(new SPRuntimeError("Internal server error [SUB-500]", HttpStatus.INTERNAL_SERVER_ERROR));
                });
    }

    private Mono<TempTransactionLogs> createTempAndExecute(String txnId, UserSessionObject user, String appData, Services service,
                                                                     String appId, boolean draft,
                                                                     ServerHttpRequest request, ServerRequest reactiveRequestObject,
                                                                     ApplicationFlowStatusEntity flowStatus, TempTransactionLogs tempTransactionLogs) {

        tempTransactionLogs.setService(service);
        tempTransactionLogs.setTxnId(txnId);
        tempTransactionLogs.setStartTime(null);

        return Mono.just(tempTransactionLogs);
    }

    private Mono<Void> validateTransaction(TempTransactionLogs txnLog, Services service) {
        if (!service.getServiceId().equals(txnLog.getService().getServiceId()) ||
                !service.getFormId().equals(txnLog.getService().getFormId())) {
            return Mono.error(new SPRuntimeError(
                    "Transaction mismatch. Please reapply. [SUB-002]", HttpStatus.BAD_REQUEST));
        }
        return Mono.empty();
    }

    private Mono<?> executeTransaction( String txnId,
                                        UserSessionObject user,
                                        String appData,
                                        Services service,
                                        String appId,
                                        boolean draft,
                                        ServerHttpRequest request, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus,
                                        TempTransactionLogs txnLog){

       return validateTransaction(txnLog, service)
                .then(
                        reactiveApiClient.saveFormData(txnId, service.getFormId(), appData)
                                .flatMap(body -> handleSuccessfulResponse(body.getBody(), service, user, request, txnLog, appId,reactiveRequestObject))
                )
                .onErrorResume(WebClientResponseException.class, Utility::handleWebClientError);
    }

    private Mono<?> handleSuccessfulResponse(
            String responseBody,
            Services service,
            UserSessionObject user,
            ServerHttpRequest request,
            TempTransactionLogs txnLog,
            String appId, ServerRequest reactiveRequestObject) {

        Map<String, String> responseJson;
        try {
            responseJson = objectMapper.readValue(responseBody, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Error parsing downstream response", e);
            return Mono.error(new SPRuntimeError("Invalid downstream response [SUB-003]", HttpStatus.BAD_GATEWAY));
        }

        String dataId = responseJson.getOrDefault("applicationId", "");
        String actionCode = responseJson.getOrDefault("actionCode", "");

        return preProcessingFacade.getFormDataAndSaveTxn(service, user, request, txnLog,appId,dataId,"FS")
                .flatMap(txn ->
                        applicationFlowDecider.proceedToNext(dataId, service, user, txn, txn.getApplicationId(),actionCode,"FS",reactiveRequestObject))
                .onErrorResume(Exception.class, ex -> {
                    Throwable actual = Exceptions.unwrap(ex);
                    if (actual instanceof SPRuntimeError spr) {
                        log.warn("Error while saving form data: {}", spr.getMessage());
                        return Mono.error(new SPRuntimeError(spr.getMessage(), spr.getErrorCode()));
                    }
                    log.error("Unexpected error while saving form data", ex);
                    return Mono.error(new SPRuntimeError("Internal server error [SUB-500]", HttpStatus.INTERNAL_SERVER_ERROR));
                });
                       // applicationGenerationService.executeApplicationProcessing(dataId, service, user, txn, appId,actionCode));
    }
}

