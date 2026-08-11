package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.APPLICATION_SUBMISSION_TASK_FLAG;
import static com.serviceplus.form.validation.utility.Utility.*;
import static java.util.Objects.isNull;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.serviceplus.form.validation.dto.*;
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
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
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
    private Mono<ServiceMeta> validateTransaction(UserSessionObject user, TempTransactionLogs txnLog, ServiceMeta service, String appData, ApplicationFlowStatusEntity flowStatus) {

        if (!service.getServiceId().equals(txnLog.getService().getServiceId()) ||
                !service.getFormId().equals(txnLog.getService().getFormId()) ||
                  !service.getTaskId().equals(txnLog.getService().getTaskId())) {
            return Mono.error(new SPRuntimeError(
                    "Transaction mismatch. Please reapply. [VAL - 001]", HttpStatus.BAD_REQUEST,txnLog.getTxnId()));
        }

        try {

            Map<String, Object> formData = objectMapper.readValue(appData, Map.class);

            Map<String, Object> selectedLocation = (Map<String, Object>) formData.get("location");

            if (service.getTaskType().equals(APPLICATION_SUBMISSION_TASK_FLAG)) {

                return reactiveApiClient
                        .fetchServiceMetadata(
                                user,
                                service.getServiceId(),
                                txnLog.getTxnId()
                        )
                        .flatMap(metadataService -> {

                            populateActionAndLocation(metadataService,service);

                            List<WorkFlowDataDTO.WorkFlowAction> availableActions = service.getAvailableActions();
                            List<ServiceMeta.AvailableApplyLocations> locations = service.getLocations();

                            if (locations == null || locations.isEmpty()) {
                                return Mono.error(new SPRuntimeError("No locations configured for this task.", HttpStatus.BAD_REQUEST, txnLog.getTxnId()));
                            }

                            if (isNull(selectedLocation) || selectedLocation.isEmpty()) {

                                if (locations.size() == 1) {

                                    service.setSelectedLocationByUser(locations.getFirst().getOrgUnitCode());
                                    service.setSelectedLocationNameByUser(locations.getFirst().getOrgUnitName());

                                } else {
                                    return Mono.error(new SPRuntimeError("Kindly select a location. [VAL - 002]", HttpStatus.BAD_REQUEST, txnLog.getTxnId()));
                                }

                            } else {

                                Long selectedLocationId = ((Number) selectedLocation.get("value")).longValue();

                                List<ServiceMeta.AvailableApplyLocations> validLocation = locations.stream()
                                                                                            .filter(loc -> selectedLocationId.equals(loc.getOrgUnitCode()))
                                                                                            .toList();

                                if (validLocation.isEmpty()) {
                                    return Mono.error(new SPRuntimeError("Invalid location selected. [SUB-003]", HttpStatus.BAD_REQUEST, txnLog.getTxnId()));
                                }

                                service.setSelectedLocationNameByUser(validLocation.getFirst().getOrgUnitName());
                                service.setSelectedLocationByUser(validLocation.getFirst().getOrgUnitCode());
                            }

                            return Mono.just(service);
                        });
            }

            return Mono.just(service);

        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return Mono.error(new SPRuntimeError("Invalid application data.", HttpStatus.BAD_REQUEST, txnLog.getTxnId()));
        }
    }

    private Mono<ServerResponse> executeTransaction( String txnId,
                                        UserSessionObject user,
                                        String appData, ServiceMeta service,
                                        String appId,
                                        boolean draft,
                                        ServerHttpRequest request, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus,
                                        TempTransactionLogs txnLog,boolean newEntityFlag){

    	return executeFormSubmissionMvel(user, service, txnLog, appData, flowStatus)
    	        .then(validateTransaction(user,txnLog, service, appData,flowStatus)
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
                                ))
                )

                .onErrorResume(WebClientResponseException.class,
                        ex -> handleWebClientError(ex, txnLog.getTxnId()));
    }

    private Mono<ServerResponse> handleSuccessfulResponse(String responseBody, ServiceMeta service, UserSessionObject user, ServerHttpRequest request, TempTransactionLogs txnLog, String appId, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus, String appData, boolean newEntityFlag) {

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

        return validateWorkflowSelection(responseJson, service, user, txnLog.getTxnId())
                .then(
                        preProcessingFacade.getFormDataAndSaveTxn(
                                service,
                                user,
                                request,
                                txnLog,
                                appId,
                                dataId,
                                "FS",
                                newEntityFlag,
                                flowStatus
                        )
                )
                .flatMap(txn -> {

                    String actionCode = "";
                    service.setWorkflowElementData(null);

                    if (service.getSelectedWorkflowElementData() != null
                            && service.getSelectedWorkflowElementData().getActionAttribute() != null
                            && !service.getSelectedWorkflowElementData().getActionAttribute().isEmpty()) {

                        actionCode = service.getSelectedWorkflowElementData()
                                .getActionAttribute()
                                .getFirst()
                                .getKey();
                    }

                    return eventDecider.proceedToNext(
                            dataId,
                            service,
                            user,
                            txn,
                            txn.getApplicationId(),
                            actionCode,
                            "FS",
                            reactiveRequestObject,
                            flowStatus);
                });
		// applicationGenerationService.executeApplicationProcessing(dataId, service,
		// user, txn, appId,actionCode));
	}


    @SuppressWarnings("unchecked")
    public Mono<Void> validateWorkflowSelection(Map<String, Object> responseJson,
                                                 ServiceMeta service,
                                                 UserSessionObject user,
                                                 String txnId) {

        if (service.getWorkflowElementData() == null) {
            applicationFlowLogs.info("TxnId : {} | Workflow metadata not present. Skipping workflow validation.", txnId);
            return Mono.empty();
        }

        applicationFlowLogs.info(responseJson);

        ServiceProcessFlowDTO.Data.WorkflowElementData workflow = service.getWorkflowElementData();

        Object actionObj = responseJson.get("action");

        List<Map<String, Object>> selectedActions;

        if (actionObj instanceof List<?> list) {
            selectedActions = (List<Map<String, Object>>) list;
        } else if (actionObj instanceof Map<?, ?> map) {
            selectedActions = List.of((Map<String, Object>) map);
        } else {
            selectedActions = Collections.emptyList();
        }

        Object taskObj = responseJson.get("task");

        List<Map<String, Object>> selectedTasks;

        if (taskObj instanceof List<?> list) {
            selectedTasks = (List<Map<String, Object>>) list;
        } else if (taskObj instanceof Map<?, ?> map) {
            selectedTasks = List.of((Map<String, Object>) map);
        } else {
            selectedTasks = Collections.emptyList();
        }

        Map<String, List<Map<String, Object>>> selectedUsers =
                responseJson.get("user") == null
                        ? Collections.emptyMap()
                        : (Map<String, List<Map<String, Object>>>) responseJson.get("user");

        ServiceProcessFlowDTO.Data.ActionAttribute selectedAction = null;

        if (!selectedActions.isEmpty() && workflow.getActionAttribute() != null) {

            String actionCode = (String) selectedActions.getFirst().get("value");

            selectedAction = workflow.getActionAttribute().stream()
                    .filter(a -> actionCode.equals(a.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new SPRuntimeError(
                            "Invalid action selected.",
                            HttpStatus.BAD_REQUEST,
                            txnId));

            applicationFlowLogs.info("TxnId : {} | Action Validated : {}", txnId, actionCode);
        }

        List<String> selectedTaskIds = new ArrayList<>();
        List<ServiceProcessFlowDTO.Data.TaskNode> selectedTaskNodes = new ArrayList<>();

        if (!selectedTasks.isEmpty()) {

            selectedTaskIds = selectedTasks.stream().map(t -> (String) t.get("value")).toList();

            Map<String, ServiceProcessFlowDTO.Data.TaskNode> allowedTaskMap =
                    workflow.getTaskAttribute().getTaskNodes().stream()
                            .collect(Collectors.toMap(
                                    ServiceProcessFlowDTO.Data.TaskNode::getTaskId,
                                    Function.identity()));

            for (String taskId : selectedTaskIds) {

                ServiceProcessFlowDTO.Data.TaskNode node = allowedTaskMap.get(taskId);

                if (node == null) {
                    throw new SPRuntimeError("Invalid task selected.", HttpStatus.BAD_REQUEST, txnId);
                }

                selectedTaskNodes.add(node);
            }

            applicationFlowLogs.info("TxnId : {} | Tasks Validated : {}", txnId, selectedTaskIds);
        }

        List<ServiceProcessFlowDTO.Data.UserNode> selectedUserNodes = new ArrayList<>();

        Mono<Void> userValidation = Mono.empty();

        if (!selectedTaskIds.isEmpty() && !selectedUsers.isEmpty()) {

            userValidation = Flux.fromIterable(selectedTaskIds)

                    .flatMap(taskId ->

                            reactiveApiClient.fetchTaskHolders(
                                            service.getServiceId(),
                                            taskId,
                                            user,
                                            txnId)

                                    .flatMap(holderResponse -> {

                                        List<FetchTaskHolders.UserNode> allowedUsers =
                                                holderResponse.getNode()
                                                        .getOrDefault(taskId, Collections.emptyList());

                                        Map<String, FetchTaskHolders.UserNode> allowedUserMap =
                                                allowedUsers.stream()
                                                        .collect(Collectors.toMap(
                                                                FetchTaskHolders.UserNode::getHolderId,
                                                                Function.identity()));

                                        List<Map<String, Object>> taskUsers =
                                                selectedUsers.getOrDefault(taskId, Collections.emptyList());

                                        for (Map<String, Object> selectedUser : taskUsers) {

                                            String holderId = (String) selectedUser.get("value");

                                            FetchTaskHolders.UserNode holder = allowedUserMap.get(holderId);

                                            if (holder == null) {
                                                return Mono.error(new SPRuntimeError("Invalid user selected.", HttpStatus.BAD_REQUEST, txnId));
                                            }

                                            ServiceProcessFlowDTO.Data.UserNode node = new ServiceProcessFlowDTO.Data.UserNode();

                                            node.setTaskId(taskId);
                                            node.setHolderId(holder.getHolderId());
                                            node.setHolderName(holder.getName());
                                            node.setLocationId(holder.getLocationId());

                                            selectedUserNodes.add(node);
                                        }

                                        return Mono.empty();
                                    }))

                    .then();
        }

        ServiceProcessFlowDTO.Data.ActionAttribute finalSelectedAction = selectedAction;

        return userValidation.then(Mono.fromRunnable(() -> {

            ServiceProcessFlowDTO.Data.WorkflowElementData selectedWorkflow = new ServiceProcessFlowDTO.Data.WorkflowElementData();

            if (finalSelectedAction != null) {
                selectedWorkflow.setActionAttribute(List.of(finalSelectedAction));
            }

            if (!selectedTaskNodes.isEmpty() && workflow.getTaskAttribute() != null) {

                ServiceProcessFlowDTO.Data.TaskAttribute taskAttribute = new ServiceProcessFlowDTO.Data.TaskAttribute();

                taskAttribute.setSelectionType(workflow.getTaskAttribute().getSelectionType());
                taskAttribute.setGatewayType(workflow.getTaskAttribute().getGatewayType());
                taskAttribute.setTaskNodes(selectedTaskNodes);

                selectedWorkflow.setTaskAttribute(taskAttribute);
            }

            if (!selectedUserNodes.isEmpty() && workflow.getUserAttribute() != null) {

                ServiceProcessFlowDTO.Data.UserAttribute userAttribute = new ServiceProcessFlowDTO.Data.UserAttribute();

                userAttribute.setSelectionType("MANUAL");
                userAttribute.setUserNodes(selectedUserNodes);

                selectedWorkflow.setUserAttribute(userAttribute);
            }

            service.setSelectedWorkflowElementData(selectedWorkflow);

            applicationFlowLogs.info("TxnId : {} | Selected Workflow Prepared Successfully", txnId);
        }));
    }

    public Mono<HandlerResponse> fetchFormData(String dataId, String formId, UserSessionObject user, String txnId, String applId, String serviceId, ServiceMeta service) {
        return reactiveApiClient.fetchApplicantData(dataId, formId, user, txnId, applId);
    }

    private Mono<Void> executeFormSubmissionMvel(
            UserSessionObject user,
            ServiceMeta service,
            TempTransactionLogs txn,
            String appData,
            ApplicationFlowStatusEntity flowStatus) {

        return reactiveApiClient.fetchMvelDetails(user, service.getServiceId(), txn.getTxnId())
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
                .then();
    }
}

