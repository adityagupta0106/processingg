package com.serviceplus.form.validation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.HandlerResponse;
import com.serviceplus.form.validation.dto.Services;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;
import com.serviceplus.form.validation.flow.EventDecider;
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

import java.util.List;
import java.util.Map;

import static com.serviceplus.form.validation.utility.ApplicationConstants.APPLICATION_SUBMISSION_TASK_FLAG;
import static com.serviceplus.form.validation.utility.ApplicationConstants.OFFICIAL_TASK_FLAG;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static com.serviceplus.form.validation.utility.Utility.handleWebClientError;

@Service
@SanitizeRequest
public class FormSubmissionService {

    @Autowired
    private ReactiveApiClient reactiveApiClient;

    @Autowired
    private ApplicationGenerationService applicationGenerationService;

    @Autowired
    private PreProcessingFacade preProcessingFacade;

    @Autowired
    private TempTransactionLogService tempTransactionLogService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventDecider eventDecider;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    public Mono<ServerResponse> applicationSubmission(ServerHttpRequest request, String txnId, String appData,
                                                      String applyKey, String appId, boolean draft, ServerRequest reactiveRequestObject
                                                     , ApplicationFlowStatusEntity flowStatus, String serviceId) {
        try {
            UserSessionObject user = getUserSessionDetails(request);

            Services service = preProcessingFacade.decryptApplyKey(applyKey);

            if(!service.getServiceId().toString().equals(serviceId)){
                return Mono.error(new SPRuntimeError("Key mismatch", HttpStatus.NOT_ACCEPTABLE));
            }

            applicationFlowLogs.info("Submitting application for txnId {} applicationId {} user {} service {} ",txnId,appId,user.getUserID(),service.toString());

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
                .onErrorResume(Exception.class, ex -> {
                    Throwable actual = Exceptions.unwrap(ex);
                    if (actual instanceof SPRuntimeError spr) {
                        applicationFlowLogs.warn("Error while saving form data: {}", spr.getMessage());
                        return Mono.error(actual);
                    }
                    applicationFlowLogs.error("Unexpected error while saving form data", ex);
                    return Mono.error(new SPRuntimeError("Internal server error [SUB-500]", HttpStatus.INTERNAL_SERVER_ERROR));
                });
    }

    private Mono<TempTransactionLogs> createTempAndExecute(String txnId, UserSessionObject user, String appData, Services service,
                                                                     String appId, boolean draft,
                                                                     ServerHttpRequest request, ServerRequest reactiveRequestObject,
                                                                     ApplicationFlowStatusEntity flowStatus, TempTransactionLogs tempTransactionLogs) {

        Services service_server = new Services();
        service_server.setServiceId(flowStatus.getServiceId());
        service_server.setFormId(flowStatus.getFormId());
        service_server.setTaskId(flowStatus.getTaskId());

        tempTransactionLogs.setService(service_server);
        tempTransactionLogs.setTxnId(txnId);

        return Mono.just(tempTransactionLogs);
    }

    private Mono<Services> validateTransaction(TempTransactionLogs txnLog, Services service, String appData) {
        if (!service.getServiceId().equals(txnLog.getService().getServiceId()) ||
                !service.getFormId().equals(txnLog.getService().getFormId()) ||
                  !service.getTaskId().equals(txnLog.getService().getTaskId())) {
            return Mono.error(new SPRuntimeError(
                    "Transaction mismatch. Please reapply. [VAL - 001]", HttpStatus.BAD_REQUEST));
        }

        try {
            Map<String,Object> formDate =  objectMapper.readValue(appData,Map.class);
            List<Services.AvailableApplyLocations> locations = service.getLocations();

            Long selectedLocation = (Long) formDate.get("location");

            if(service.getTaskType().equals(APPLICATION_SUBMISSION_TASK_FLAG)) {

                if (selectedLocation == null) {
                    if (locations.size() == 1) {
                        service.setSelectedLocationByUser(locations.getFirst().getLocationId());
                        service.setSelectedLocationNameByUser(locations.getFirst().getLocationName());
                    } else {
                        return Mono.error(new SPRuntimeError(
                                "Kindly select a location. [VAL - 002]", HttpStatus.BAD_REQUEST));
                    }
                } else {
                    List<Services.AvailableApplyLocations> validLocation = locations.stream()
                            .filter(loc -> loc.getLocationId().equals(selectedLocation))
                            .toList();
                    if (validLocation.isEmpty()) {
                        return Mono.error(new SPRuntimeError(
                                "Invalid location selected. [SUB-003]", HttpStatus.BAD_REQUEST));
                    }

                    service.setSelectedLocationNameByUser(validLocation.getFirst().getLocationName());
                    service.setSelectedLocationByUser(validLocation.getFirst().getLocationId());
                }
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return Mono.just(service);
    }

    private Mono<ServerResponse> executeTransaction( String txnId,
                                        UserSessionObject user,
                                        String appData,
                                        Services service,
                                        String appId,
                                        boolean draft,
                                        ServerHttpRequest request, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus,
                                        TempTransactionLogs txnLog){

       return validateTransaction(txnLog, service,appData)
                .flatMap( serviceModified ->
                        reactiveApiClient.saveFormData(txnId, service, appData,user)
                                .flatMap(body -> handleSuccessfulResponse(body.getBody(), serviceModified, user, request, txnLog, appId,reactiveRequestObject,flowStatus))
                )
                .onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex,txnLog.getTxnId()));
    }

    private Mono<ServerResponse> handleSuccessfulResponse(
            String responseBody,
            Services service,
            UserSessionObject user,
            ServerHttpRequest request,
            TempTransactionLogs txnLog,
            String appId, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus) {

        Map<String, String> responseJson;
        applicationFlowLogs.info("Response from form management for txnId {} applicationId {} response {}",txnLog.getTxnId(),appId,responseBody);
        //preProcessingFacade.saveTransactionReactive();

        try {
            responseJson = objectMapper.readValue(responseBody, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            applicationFlowLogs.error("Error parsing downstream response", e);
            return Mono.error(new SPRuntimeError("Invalid downstream response [SUB-003]", HttpStatus.BAD_GATEWAY));
        }

        String dataId = responseJson.getOrDefault("dataId", "");
        String actionCode = responseJson.getOrDefault("actionCode", "");

        return preProcessingFacade.getFormDataAndSaveTxn(service, user, request, txnLog,appId,dataId,"FS")
                .flatMap(txn ->
                        eventDecider.proceedToNext(dataId, service, user, txn, txn.getApplicationId(),actionCode,"FS",reactiveRequestObject,flowStatus))
                .onErrorResume(Exception.class, ex -> {
                    Throwable actual = Exceptions.unwrap(ex);
                    if (actual instanceof SPRuntimeError spr) {
                        applicationFlowLogs.warn("Error while saving form data: {}", spr.getMessage());
                        return Mono.error(new SPRuntimeError(spr.getMessage(), spr.getErrorCode()));
                    }
                    applicationFlowLogs.error("Unexpected error while saving form data", ex);
                    return Mono.error(new SPRuntimeError("Internal server error [SUB-500]", HttpStatus.INTERNAL_SERVER_ERROR));
                });
                       // applicationGenerationService.executeApplicationProcessing(dataId, service, user, txn, appId,actionCode));
    }
}

