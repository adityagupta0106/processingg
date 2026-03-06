package com.serviceplus.form.validation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.flow.EventDecider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
import static com.serviceplus.form.validation.utility.Utility.*;
import static java.util.Objects.isNull;

@Service
@SanitizeRequest
public class FormService {

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

    public Mono<ServerResponse> applicationSubmission(ServerHttpRequest request, String txnId, String appData
                                                     , String appId, boolean draft, ServerRequest reactiveRequestObject
                                                     , ApplicationFlowStatusEntity flowStatus, String serviceId,ServiceMeta service) {
        try {
            UserSessionObject user = getUserSessionDetails(request);

            if(!service.getServiceId().toString().equals(serviceId)){
                return Mono.error(new SPRuntimeError("Key mismatch", HttpStatus.NOT_ACCEPTABLE,txnId));
            }

            applicationFlowLogs.info("Submitting application for txnId {} applicationId {} user {} service {} ",txnId,appId,user.getUserID(),service.toString());

            if(appId.isEmpty() && service.getTaskType().equals(OFFICIAL_TASK_FLAG)){
                return Mono.error(new SPRuntimeError(
                        "Issue while processing the request [SUB - 008]",
                        HttpStatus.INTERNAL_SERVER_ERROR,txnId
                ));
            }

            return saveFormData(txnId, user,appData,service,appId,draft,request,reactiveRequestObject,flowStatus);

        } catch (Exception e) {
            e.printStackTrace();
            return Mono.error(new SPRuntimeError("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR,txnId));
        }
    }


    public Mono<ServerResponse> saveFormData(
            String txnId,
            UserSessionObject user,
            String appData,
            ServiceMeta service,
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
                    return Mono.error(new SPRuntimeError("Internal server error [SUB-500]", HttpStatus.INTERNAL_SERVER_ERROR,txnId));
                });
    }

    private Mono<TempTransactionLogs> createTempAndExecute(String txnId, UserSessionObject user, String appData, ServiceMeta service,
                                                                     String appId, boolean draft,
                                                                     ServerHttpRequest request, ServerRequest reactiveRequestObject,
                                                                     ApplicationFlowStatusEntity flowStatus, TempTransactionLogs tempTransactionLogs) {

        ServiceMeta service_server = new ServiceMeta();
        service_server.setServiceId(flowStatus.getServiceId());
        service_server.setFormId(flowStatus.getFormId());
        service_server.setTaskId(flowStatus.getTaskId());

        tempTransactionLogs.setService(service_server);
        tempTransactionLogs.setTxnId(txnId);

        return Mono.just(tempTransactionLogs);
    }

    @SuppressWarnings("unchecked")
    private Mono<ServiceMeta> validateTransaction(TempTransactionLogs txnLog, ServiceMeta service, String appData) {
        if (!service.getServiceId().equals(txnLog.getService().getServiceId()) ||
                !service.getFormId().equals(txnLog.getService().getFormId()) ||
                  !service.getTaskId().equals(txnLog.getService().getTaskId())) {
            return Mono.error(new SPRuntimeError(
                    "Transaction mismatch. Please reapply. [VAL - 001]", HttpStatus.BAD_REQUEST,txnLog.getTxnId()));
        }

        try {
            Map<String,Object> formDate =  objectMapper.readValue(appData,Map.class);
            List<ServiceMeta.AvailableApplyLocations> locations = service.getLocations();

            Map<String,Object> selectedLocation = (Map<String,Object>) formDate.get("location");

            if(service.getTaskType().equals(APPLICATION_SUBMISSION_TASK_FLAG)) {

                if (isNull(selectedLocation) || selectedLocation.isEmpty()) {
                    if (locations.size() == 1) {
                        service.setSelectedLocationByUser(locations.getFirst().getLocationId());
                        service.setSelectedLocationNameByUser(locations.getFirst().getLocationName());
                    } else {
                        return Mono.error(new SPRuntimeError(
                                "Kindly select a location. [VAL - 002]", HttpStatus.BAD_REQUEST,txnLog.getTxnId()));
                    }
                } else {
                    List<ServiceMeta.AvailableApplyLocations> validLocation = locations.stream()
                            .filter(loc -> loc.getLocationId().intValue() == (Integer) selectedLocation.get("value"))
                            .toList();
                    if (validLocation.isEmpty()) {
                        return Mono.error(new SPRuntimeError(
                                "Invalid location selected. [SUB-003]", HttpStatus.BAD_REQUEST,txnLog.getTxnId()));
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
                                        String appData, ServiceMeta service,
                                        String appId,
                                        boolean draft,
                                        ServerHttpRequest request, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus,
                                        TempTransactionLogs txnLog){

       return validateTransaction(txnLog, service,appData)
                .flatMap( serviceModified ->
                        reactiveApiClient.saveFormData(txnId, service, appData,user,flowStatus.getDataId())
                                .flatMap(body -> handleSuccessfulResponse(body.getBody(), serviceModified, user, request, txnLog, appId,reactiveRequestObject,flowStatus))
                )
                .onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex,txnLog.getTxnId()));
    }

    private Mono<ServerResponse> handleSuccessfulResponse(
            String responseBody,
            ServiceMeta service,
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
            return Mono.error(new SPRuntimeError("Invalid downstream response [SUB-003]", HttpStatus.BAD_GATEWAY,txnLog.getTxnId()));
        }

        String dataId = responseJson.getOrDefault("dataId", "");
        String actionCode = responseJson.getOrDefault("actionCode", "");

        return preProcessingFacade.getFormDataAndSaveTxn(service, user, request, txnLog,appId,dataId,"FS")
                .flatMap(txn ->
                        eventDecider.proceedToNext(dataId, service, user, txn, txn.getApplicationId(),actionCode,"FS",reactiveRequestObject,flowStatus))
                .onErrorResume(Exception.class, ex -> returnError(ex,txnLog.getTxnId(),applicationFlowLogs));
                       // applicationGenerationService.executeApplicationProcessing(dataId, service, user, txn, appId,actionCode));
    }

    public Mono<ServerResponse> fetchFormData(String dataId, String formId, UserSessionObject user, String txnId, String applId, String serviceId, ServiceMeta service) {
        return reactiveApiClient.fetchApplicantData(dataId,formId,user,txnId,applId);
    }
}

