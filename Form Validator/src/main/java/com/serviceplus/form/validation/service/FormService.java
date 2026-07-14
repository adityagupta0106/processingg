package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.APPLICATION_SUBMISSION_TASK_FLAG;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static com.serviceplus.form.validation.utility.Utility.handleWebClientError;
import static com.serviceplus.form.validation.utility.Utility.returnError;
import static java.util.Objects.isNull;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.flow.EventDecider;

import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
                                                     , ApplicationFlowStatusEntity flowStatus, String serviceId,ServiceMeta service,boolean newEntityFlag) {
        try {
            UserSessionObject user = getUserSessionDetails(request);

            if(!service.getServiceId().toString().equals(serviceId)){
                return Mono.error(new SPRuntimeError("Key mismatch", HttpStatus.NOT_ACCEPTABLE,txnId));
            }

            applicationFlowLogs.info("Submitting application for txnId {} applicationId {} user {} service {} ",txnId,appId,user.getUserID(),service.toString());

            return saveFormData(txnId, user,appData,service,appId,draft,request,reactiveRequestObject,flowStatus,newEntityFlag);

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
            ServerHttpRequest request, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus,boolean newEntityFlag) {

        return tempTransactionLogService.fetch(txnId)
                .switchIfEmpty(
                        createTempAndExecute(txnId,user,appData,service,appId,draft,request,reactiveRequestObject, flowStatus,new TempTransactionLogs())
                 )
                 .flatMap(
                         txnLog -> executeTransaction(txnId,user,appData,service,appId,draft,request,reactiveRequestObject, flowStatus,txnLog,newEntityFlag)
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
            Map<String,Object> formData =  objectMapper.readValue(appData,Map.class);
            List<ServiceMeta.AvailableApplyLocations> locations = service.getLocations();

            Map<String,Object> selectedLocation = (Map<String,Object>) formData.get("location");

            if(service.getTaskType().equals(APPLICATION_SUBMISSION_TASK_FLAG)) {

                if (isNull(selectedLocation) || selectedLocation.isEmpty()) {
                    if (locations.size() == 1) {
                        service.setSelectedLocationByUser(locations.getFirst().getOrgUnitCode());
                        service.setSelectedLocationNameByUser(locations.getFirst().getOrgUnitName());
                    } else {
                        return Mono.error(new SPRuntimeError(
                                "Kindly select a location. [VAL - 002]", HttpStatus.BAD_REQUEST,txnLog.getTxnId()));
                    }
                } else {
                    List<ServiceMeta.AvailableApplyLocations> validLocation = locations.stream()
                            .filter(loc -> loc.getOrgUnitCode().intValue() == (Integer) selectedLocation.get("value"))
                            .toList();
                    if (validLocation.isEmpty()) {
                        return Mono.error(new SPRuntimeError(
                                "Invalid location selected. [SUB-003]", HttpStatus.BAD_REQUEST,txnLog.getTxnId()));
                    }

                    service.setSelectedLocationNameByUser(validLocation.getFirst().getOrgUnitName());
                    service.setSelectedLocationByUser(validLocation.getFirst().getOrgUnitCode());
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
                                        TempTransactionLogs txnLog,boolean newEntityFlag){
    	
    	return validateTransaction(txnLog, service, appData)
                    .flatMap(serviceModified ->
                        reactiveApiClient.saveFormData(
                                        txnId,
                                        serviceModified,
                                        appData,
                                        user,
                                        flowStatus.getDataId(),
                                        appId
                                )
                                .flatMap(body ->
                                        handleSuccessfulResponse(
                                                body.getBody(),
                                                serviceModified,
                                                user,
                                                request,
                                                txnLog,
                                                appId,
                                                reactiveRequestObject,
                                                flowStatus,
                                                appData,newEntityFlag
                                        )
                                )
                )

                .onErrorResume(WebClientResponseException.class,
                        ex -> handleWebClientError(ex, txnLog.getTxnId()));
    }

    private Mono<ServerResponse> handleSuccessfulResponse(
            String responseBody,
            ServiceMeta service,
            UserSessionObject user,
            ServerHttpRequest request,
            TempTransactionLogs txnLog,
            String appId,
            ServerRequest reactiveRequestObject,
            ApplicationFlowStatusEntity flowStatus,
			String appData,boolean newEntityFlag) {

		Map<String, Object> responseJson;

		applicationFlowLogs.info("Response from form management for txnId {} applicationId {} response {}",
				txnLog.getTxnId(), appId, responseBody);

		try {
			responseJson = objectMapper.readValue(responseBody, new TypeReference<>() {
			});
		} catch (JsonProcessingException e) {
			applicationFlowLogs.error("Error parsing downstream response", e);
			return Mono.error(new SPRuntimeError("Invalid downstream response [SUB-003]", HttpStatus.BAD_GATEWAY,
					txnLog.getTxnId()));
		}

		String dataId = (String) responseJson.getOrDefault("dataId", "");
		String actionCode = (String) responseJson.getOrDefault("actionCode", "");

        return preProcessingFacade.getFormDataAndSaveTxn(service, user, request, txnLog, appId, dataId, "FS",newEntityFlag,flowStatus)
				.flatMap(txn ->  eventDecider.proceedToNext(dataId, service, user, txn, txn.getApplicationId(),
								actionCode, "FS", reactiveRequestObject, flowStatus)
						)
				.onErrorResume(Exception.class, ex -> returnError(ex, txnLog.getTxnId(), applicationFlowLogs));
		// applicationGenerationService.executeApplicationProcessing(dataId, service,
		// user, txn, appId,actionCode));
	}

    public Mono<ServerResponse> fetchFormData(String dataId, String formId, UserSessionObject user, String txnId, String applId, String serviceId, ServiceMeta service) {
        return reactiveApiClient.fetchApplicantData(dataId,formId,user,txnId,applId);
    }

    private Mono<ServerResponse> executeFormSubmissionMvel(
            ServiceMeta service,
            TempTransactionLogs txn,
            String appData,
            ApplicationFlowStatusEntity flowStatus) {

        return reactiveApiClient.fetchMvelDetails(service.getServiceId())
                .flatMapMany(Flux::fromIterable)
                .filter(m -> "FS".equalsIgnoreCase(m.getValue()))
                .filter(m -> m.getNodeId().equals(flowStatus.getTaskId()))
                .flatMap(m ->
                        reactiveApiClient.executeMvel(
                                        m.getMvelId(),
                                        txn.getTxnId(),                  
                                        flowStatus.getId(),          
                                        "FS",                             
                                        flowStatus.getApplicationId(),   
                                        service.getServiceId(),
                                        null,
                                        appData,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                                .flatMap(mvelResponse -> {
                                    if (!mvelResponse.isSuccess()) {
                                        return Mono.error(new SPRuntimeError(
                                                "Invalid downstream response [MVEL-001]",
                                                HttpStatus.BAD_GATEWAY,
                                                txn.getTxnId()
                                        ));
                                    }

                                    List<Map<String, Object>> attributeResponse =
                                            mvelResponse.getAttributeResponse();
                                    boolean hasError = attributeResponse != null &&
                                            attributeResponse.stream()
                                                    .anyMatch(attr -> attr.get("error") != null);
                                    if (hasError) {
                                        SPRuntimeError error = new SPRuntimeError(
                                                "Validation Error",
                                                HttpStatus.BAD_REQUEST,
                                                txn.getTxnId()
                                        );
                                        error.setData(mvelResponse.getDataResponse());
                                        return Mono.error(error);
                                    }

                                    return Mono.empty(); 
                                })
                )
                .then(ServerResponse.ok().build());
    }
}

