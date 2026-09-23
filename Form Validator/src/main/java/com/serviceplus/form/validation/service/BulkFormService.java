package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_BULK_STATUS_KEY;
import static com.serviceplus.form.validation.utility.ApplicationConstants.APPLICATION_SUBMISSION_TASK_FLAG;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static com.serviceplus.form.validation.utility.Utility.handleWebClientError;
import static java.util.Objects.isNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.EntityResponse;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.flow.EventDecider;

import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@SanitizeRequest
public class BulkFormService {

	private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

	private final ReactiveApiClient reactiveApiClient;
	private final PreProcessingFacade preProcessingFacade;
	private final TempTransactionLogService tempTransactionLogService;
	private final ObjectMapper objectMapper;
	private final EventDecider eventDecider;

	public BulkFormService(ReactiveApiClient reactiveApiClient, PreProcessingFacade preProcessingFacade,
			TempTransactionLogService tempTransactionLogService, ObjectMapper objectMapper, EventDecider eventDecider) {

		this.reactiveApiClient = reactiveApiClient;
		this.preProcessingFacade = preProcessingFacade;
		this.tempTransactionLogService = tempTransactionLogService;
		this.objectMapper = objectMapper;
		this.eventDecider = eventDecider;
	}

   
	public Mono<ServerResponse> applicationSubmission(ServerHttpRequest request, String txnId, String appData,
			String applicationIds, boolean draft, ServerRequest reactiveRequestObject,
			ApplicationFlowStatusEntity sourceFlowStatus, String serviceId, ServiceMeta service,
			boolean newEntityFlag) {

		try {

			UserSessionObject user = getUserSessionDetails(request);

			if (isNull(user)) {
				return Mono.error(new SPRuntimeError("Unauthorized user", HttpStatus.UNAUTHORIZED, txnId));
			}

			if (service == null || service.getServiceId() == null) {
				return Mono.error(new SPRuntimeError("Service information is missing", HttpStatus.BAD_REQUEST, txnId));
			}

			if (!service.getServiceId().toString().equals(serviceId)) {
				return Mono.error(new SPRuntimeError("Key mismatch", HttpStatus.NOT_ACCEPTABLE, txnId));
			}

			List<String> applicationIdList = splitApplicationIds(applicationIds);

			boolean bulkEnabled = service.getWorkflowElementData() != null
					&& Boolean.TRUE.equals(service.getWorkflowElementData().getBulkEnabled());
			bulkEnabled=true;
			if (!bulkEnabled) {
				return Mono.error(new SPRuntimeError("Bulk processing is not enabled for this task",
						HttpStatus.BAD_REQUEST, txnId));
			}
			if (applicationIdList.size() < 2) {
				return Mono.error(new SPRuntimeError("BULKFS requires at least two application ids",
						HttpStatus.BAD_REQUEST, txnId));
			}

			applicationFlowLogs.info("BULKFS started. txnId={} applications={} user={} service={}", txnId,
					applicationIdList, user.getUserID(), service.getServiceId());

			return saveBulkFormData(txnId, user, appData, service, applicationIdList, draft, request,
					reactiveRequestObject, sourceFlowStatus, newEntityFlag);

		} catch (Exception e) {

			applicationFlowLogs.error("Unexpected error in BULKFS. txnId={}", txnId, e);

			return Mono.error(new SPRuntimeError("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR, txnId));
		}
	}

	private Mono<ServerResponse> saveBulkFormData(String txnId, UserSessionObject user, String appData,
			ServiceMeta service, List<String> applicationIds, boolean draft, ServerHttpRequest request,
			ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity sourceFlowStatus, boolean newEntityFlag) {

		String applicationIdValue = String.join(",", applicationIds);

		return tempTransactionLogService.fetch(txnId)

				.switchIfEmpty(createTempAndExecute(txnId, service, sourceFlowStatus))

				.flatMap(txnLog -> validateTransaction(txnLog, service, appData))

				.flatMap(serviceModified ->

				reactiveApiClient
						.saveFormData(txnId, serviceModified, appData, user, sourceFlowStatus.getDataId(),
								applicationIdValue, false)

						.flatMap(response ->

						handleBulkSuccessfulResponse(response.getBody(), serviceModified, user, request, txnId,
								applicationIds, reactiveRequestObject, sourceFlowStatus, appData, newEntityFlag)))

				.onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex, txnId))

				.onErrorResume(Exception.class, ex -> {

					Throwable actual = Exceptions.unwrap(ex);

					if (actual instanceof SPRuntimeError) {
						return Mono.error(actual);
					}

					applicationFlowLogs.error("Unexpected error during BULKFS. txnId={}", txnId, ex);

					return Mono.error(new SPRuntimeError("Internal server error [SUB-500]",
							HttpStatus.INTERNAL_SERVER_ERROR, txnId));
				});
	}

	private Mono<TempTransactionLogs> createTempAndExecute(String txnId, ServiceMeta service,
			ApplicationFlowStatusEntity flowStatus) {

		TempTransactionLogs tempTransactionLogs = new TempTransactionLogs();

		ServiceMeta serviceServer = new ServiceMeta();

		serviceServer.setServiceId(flowStatus.getServiceId());

		serviceServer.setFormId(flowStatus.getFormId());

		serviceServer.setTaskId(flowStatus.getTaskId());

		tempTransactionLogs.setService(serviceServer);
		tempTransactionLogs.setTxnId(txnId);

		return Mono.just(tempTransactionLogs);
	}

    
    @SuppressWarnings("unchecked")
	private Mono<ServiceMeta> validateTransaction(TempTransactionLogs txnLog, ServiceMeta service, String appData) {

		if (!service.getServiceId().equals(txnLog.getService().getServiceId())
				|| !service.getFormId().equals(txnLog.getService().getFormId())
				|| !service.getTaskId().equals(txnLog.getService().getTaskId())) {

			return Mono.error(new SPRuntimeError("Transaction mismatch. Please retry. [VAL - 001]",
					HttpStatus.BAD_REQUEST, txnLog.getTxnId()));
		}

		try {

			Map<String, Object> formData = objectMapper.readValue(appData, Map.class);

			List<ServiceMeta.AvailableApplyLocations> locations = service.getLocations();

			Map<String, Object> selectedLocation = (Map<String, Object>) formData.get("location");

			if (APPLICATION_SUBMISSION_TASK_FLAG.equals(service.getTaskType())) {

				if (isNull(selectedLocation) || selectedLocation.isEmpty()) {

					if (locations.size() == 1) {

						service.setSelectedLocationByUser(locations.getFirst().getOrgUnitCode());

						service.setSelectedLocationNameByUser(locations.getFirst().getOrgUnitName());

					} else {

						return Mono.error(new SPRuntimeError("Kindly select a location. [VAL - 002]",
								HttpStatus.BAD_REQUEST, txnLog.getTxnId()));
					}

				} else {

					Long selectedLocationId = ((Number) selectedLocation.get("value")).longValue();

					List<ServiceMeta.AvailableApplyLocations> validLocation = locations.stream()
							.filter(location -> selectedLocationId.equals(location.getOrgUnitCode())).toList();

					if (validLocation.isEmpty()) {

						return Mono.error(new SPRuntimeError("Invalid location selected. [SUB-003]",
								HttpStatus.BAD_REQUEST, txnLog.getTxnId()));
					}

					service.setSelectedLocationNameByUser(validLocation.getFirst().getOrgUnitName());

					service.setSelectedLocationByUser(validLocation.getFirst().getOrgUnitCode());
				}
			}

		} catch (JsonProcessingException e) {

			return Mono.error(new SPRuntimeError("Invalid form data", HttpStatus.BAD_REQUEST, txnLog.getTxnId()));
		}

		return Mono.just(service);
	}

	private Mono<ServerResponse> handleBulkSuccessfulResponse(String responseBody, ServiceMeta service,
			UserSessionObject user, ServerHttpRequest request, String txnId, List<String> applicationIds,
			ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity sourceFlowStatus, String appData,
			boolean newEntityFlag) {

		applicationFlowLogs.info("BULKFS response. txnId={} applications={} response={}", txnId, applicationIds,
				responseBody);

		Map<String, Object> responseJson;

		try {

			responseJson = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {
			});

		} catch (JsonProcessingException e) {

			applicationFlowLogs.error("Unable to parse Form Management response. txnId={}", txnId, e);

			return Mono
					.error(new SPRuntimeError("Invalid downstream response [SUB-003]", HttpStatus.BAD_GATEWAY, txnId));
		}

		String dataId = (String) responseJson.getOrDefault("dataId", "");

		if (dataId == null || dataId.isBlank()) {

			return Mono.error(
					new SPRuntimeError("Data id not returned by Form Management", HttpStatus.BAD_GATEWAY, txnId));
		}

		return validateWorkflowSelection(responseJson, service, user, txnId)

				.thenMany(
		                createApplicationFlowStatusForApplications(
		                        applicationIds,
		                        sourceFlowStatus,
		                        dataId,
		                        txnId))
				.thenMany(proceedApplications(applicationIds, dataId, service, user, txnId, reactiveRequestObject,
						sourceFlowStatus))

				.collectList()

				.flatMap(applicationResponses -> {

					Map<String, Object> finalResponse = new LinkedHashMap<>();

					finalResponse.put("bulkResponse", true);
					finalResponse.put("applications", applicationResponses);

					return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(finalResponse);
				});
	}

	private Flux<ApplicationFlowStatusEntity> createApplicationFlowStatusForApplications(List<String> applicationIds,
			ApplicationFlowStatusEntity sourceFlowStatus, String dataId, String txnId) {

		return Flux.fromIterable(applicationIds).map(applicationId -> {

			ApplicationFlowStatusEntity flow = new ApplicationFlowStatusEntity();

			flow.setApplicationId(applicationId);
			flow.setTxnId(txnId);
			flow.setFormId(sourceFlowStatus.getFormId());
			flow.setServiceId(sourceFlowStatus.getServiceId());
			flow.setTaskId(sourceFlowStatus.getTaskId());
			flow.setTenantId(sourceFlowStatus.getTenantId());
			flow.setDataId(dataId);
			flow.setActivityType(ACTIVITY_FORM_BULK_STATUS_KEY);
			flow.setCompleted(0);
			flow.setNewEntity(sourceFlowStatus.isNewEntity());
			flow.setLastUpdate(sourceFlowStatus.getLastUpdate());

			return flow;
		});
	}

	private Flux<Map<String, Object>> proceedApplications(List<String> applicationIds, String dataId,
			ServiceMeta service, UserSessionObject user, String txnId, ServerRequest reactiveRequestObject,
			ApplicationFlowStatusEntity sourceFlowStatus) {

		String actionCode = getActionCode(service);

		return Flux.fromIterable(applicationIds).map(String::trim).filter(applicationId -> !applicationId.isBlank())

				.concatMap(applicationId -> {

					applicationFlowLogs.info("Starting bulk processing. applicationId={} txnId={} dataId={}",
							applicationId, txnId, dataId);

					ApplicationFlowStatusEntity flow = createFlowForApplication(sourceFlowStatus, applicationId, dataId,
							txnId);

					TempTransactionLogs txnLog = createTxnLog(txnId, service, flow);

					return preProcessingFacade
							.getFormDataAndSaveTxn(service, user, reactiveRequestObject.exchange().getRequest(), txnLog,
									applicationId, dataId, ACTIVITY_FORM_BULK_STATUS_KEY, false, flow, "")

							.flatMap(processingTxn -> {

								applicationFlowLogs.info("Transaction saved. applicationId={} txnId={}", applicationId,
										processingTxn.getTxnId());

								return eventDecider
										.proceedToNext(dataId, service, user, processingTxn, applicationId, actionCode,
												ACTIVITY_FORM_BULK_STATUS_KEY, reactiveRequestObject, flow)
										.flatMap(this::captureResponseBody);
							})

							.doOnSuccess(result -> applicationFlowLogs.info(
									"Bulk application completed. applicationId={} txnId={}", applicationId, txnId))

							.doOnError(ex -> applicationFlowLogs.error(
									"Bulk application failed. applicationId={} txnId={} error={}", applicationId, txnId,
									ex.getMessage(), ex));
				});
	}
	
	private Mono<Map<String, Object>> captureResponseBody(ServerResponse serverResponse) {

		if (serverResponse instanceof EntityResponse<?> entityResponse) {

			Object entity = entityResponse.entity();

			if (entity == null) {
				return Mono.empty();
			}

			try {

				String json = objectMapper.writeValueAsString(entity);

				Map<String, Object> response = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
				});

				return Mono.just(response);

			} catch (JsonProcessingException e) {

				return Mono.error(e);
			}
		}

		return Mono.error(new IllegalStateException("Workflow response does not contain an entity body"));
	}
	

	private ApplicationFlowStatusEntity createFlowForApplication(ApplicationFlowStatusEntity source,
			String applicationId, String dataId, String txnId) {

		ApplicationFlowStatusEntity flow = new ApplicationFlowStatusEntity();

		flow.setApplicationId(applicationId);
		flow.setTxnId(txnId);
		flow.setFormId(source.getFormId());
		flow.setServiceId(source.getServiceId());
		flow.setTaskId(source.getTaskId());
		flow.setTenantId(source.getTenantId());
		flow.setDataId(dataId);
		flow.setActivityType(ACTIVITY_FORM_BULK_STATUS_KEY);
		flow.setCompleted(0);
		flow.setNewEntity(source.isNewEntity());
		flow.setLastUpdate(source.getLastUpdate());

		return flow;
	}

	private TempTransactionLogs createTxnLog(String txnId, ServiceMeta service, ApplicationFlowStatusEntity flow) {

		TempTransactionLogs txn = new TempTransactionLogs();

		txn.setTxnId(txnId);

		ServiceMeta txnService = new ServiceMeta();

		txnService.setServiceId(service.getServiceId());

		txnService.setFormId(service.getFormId());

		txnService.setTaskId(service.getTaskId());

		txn.setService(txnService);

		return txn;
	}

	private String getActionCode(ServiceMeta service) {

		if (service.getSelectedWorkflowElementData() == null
				|| service.getSelectedWorkflowElementData().getActionAttribute() == null
				|| service.getSelectedWorkflowElementData().getActionAttribute().isEmpty()) {

			return "";
		}

		return service.getSelectedWorkflowElementData().getActionAttribute().getFirst().getKey();
	}

    @SuppressWarnings("unchecked")
	private Mono<Void> validateWorkflowSelection(Map<String, Object> responseJson, ServiceMeta service,
			UserSessionObject user, String txnId) {

		if (service.getWorkflowElementData() == null) {
			applicationFlowLogs.info("TxnId : {} | Workflow metadata not present.", txnId);
			return Mono.empty();
		}

		ServiceProcessFlowDTO.Data.WorkflowElementData workflow = service.getWorkflowElementData();

		List<Map<String, Object>> selectedActions = (List<Map<String, Object>>) responseJson.getOrDefault("action",
				Collections.emptyList());

		List<Map<String, Object>> selectedTasks = (List<Map<String, Object>>) responseJson.getOrDefault("task",
				Collections.emptyList());

		Map<String, List<Map<String, Object>>> selectedUsers = responseJson.get("user") == null ? Collections.emptyMap()
				: (Map<String, List<Map<String, Object>>>) responseJson.get("user");

		ServiceProcessFlowDTO.Data.ActionAttribute selectedAction = null;

		if (!selectedActions.isEmpty() && workflow.getActionAttribute() != null) {

			String actionCode = (String) selectedActions.getFirst().get("key");

			selectedAction = workflow.getActionAttribute().stream().filter(a -> actionCode.equals(a.getKey()))
					.findFirst()
					.orElseThrow(() -> new SPRuntimeError("Invalid action selected.", HttpStatus.BAD_REQUEST, txnId));
		}

		List<ServiceProcessFlowDTO.Data.TaskNode> selectedTaskNodes = new java.util.ArrayList<>();

		if (!selectedTasks.isEmpty()) {

			Map<String, ServiceProcessFlowDTO.Data.TaskNode> allowedTaskMap = workflow.getTaskAttribute().getTaskNodes()
					.stream()
					.collect(Collectors.toMap(ServiceProcessFlowDTO.Data.TaskNode::getTaskId, Function.identity()));

			for (Map<String, Object> task : selectedTasks) {

				String taskId = (String) task.get("key");

				ServiceProcessFlowDTO.Data.TaskNode node = allowedTaskMap.get(taskId);

				if (node == null) {
					return Mono.error(new SPRuntimeError("Invalid task selected.", HttpStatus.BAD_REQUEST, txnId));
				}

				selectedTaskNodes.add(node);
			}
		}

		ServiceProcessFlowDTO.Data.ActionAttribute finalSelectedAction = selectedAction;

		return Mono.fromRunnable(() -> {

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

			service.setSelectedWorkflowElementData(selectedWorkflow);

			applicationFlowLogs.info("TxnId : {} | BULKFS workflow selection prepared", txnId);
		});
    }

	private List<String> splitApplicationIds(String applicationIds) {

		if (applicationIds == null || applicationIds.isBlank()) {
			return List.of();
		}

		return Arrays.stream(applicationIds.split(",")).map(String::trim)
				.filter(id -> !id.isBlank()).distinct()
				.toList();
	}
}
