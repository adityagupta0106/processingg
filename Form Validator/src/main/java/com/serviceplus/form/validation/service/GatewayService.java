package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_EXCLUSIVE_DIVERGENT;
import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_INCLUSIVE_CONVERGENT;
import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_INCLUSIVE_DIVERGENT;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactivestreams.Publisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.Helpers.CurrentProcessBuilder;
import com.serviceplus.form.validation.Helpers.WorkflowHelper;
import com.serviceplus.form.validation.config.ConvergentGatewayFactory;
import com.serviceplus.form.validation.dto.ApplicationRouting;
import com.serviceplus.form.validation.dto.OfficeDetailsDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.TaskAvailableOfficeLocation;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.Data;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.Data.Nodes;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.TaskRelationDTO;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.handlers.ConvergentGatewayHandler;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class GatewayService {

    private final ReactiveApiClient apiClient;

    private final WorkflowHelper workflowHelper;

    private final TaskAssignmentService taskAssignmentService;

    private final CurrentProcessBuilder currentProcessBuilder;
    
    private final ConvergentGatewayFactory convergentGatewayFactory;

    private final CurrentProcessRepository currentProcessRepository;
    
    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    public GatewayService(ReactiveApiClient apiClient, WorkflowHelper workflowHelper, TaskAssignmentService taskAssignmentService, CurrentProcessBuilder currentProcessBuilder, CurrentProcessRepository currentProcessRepository, ConvergentGatewayFactory convergentGatewayFactory) {
        this.apiClient = apiClient;
        this.workflowHelper = workflowHelper;
        this.taskAssignmentService = taskAssignmentService;
        this.currentProcessBuilder = currentProcessBuilder;
		this.convergentGatewayFactory = convergentGatewayFactory;
		this.currentProcessRepository = currentProcessRepository;
    }

	public Flux<CurrentProcess> processGateway(ServiceProcessFlowDTO.Data.Nodes node,
			ServiceProcessFlowDTO.Data.Nodes gatewayNode, List<ServiceProcessFlowDTO.Data> workflow, ServiceMeta service,
			ApplicationDetails ad, ProcessingTxn txn, UserSessionObject user, LocalDateTime now,
			CurrentProcess currentActionProcess, CurrentProcess baseProcess,
			List<TaskAvailableOfficeLocation> taskAvailableOfficeLocations,
			Map<String, Map<String, List<String>>> taskLocationUserHolderMap,
			ServiceProcessFlowDTO.Data.WorkflowElementData selectedWorkflow, Map<String, LocalDateTime> timerDueDate,
			Map<String, TaskRelationDTO> taskRelationMap, List<OfficeDetailsDTO> officeDetails,
			Map<String, ApplicationRouting> applicationRoutingMap) {
	
		applicationFlowLogs.info("Gateway encountered gatewayId={}, behaviour={}", gatewayNode.getId(),
				gatewayNode.getBehaviour());
	
		ServiceProcessFlowDTO.Data nextToGatewayData = workflowHelper.fetchNode(workflow, gatewayNode.getId());
	
		List<ServiceProcessFlowDTO.Data.MappedTask> nextToGateway = nextToGatewayData.getMappedTasks();
	
		applicationFlowLogs.info("Gateway next nodes={}", nextToGateway.stream().map(t -> t.getNode().getId()).toList());
	
		String behaviour = gatewayNode.getBehaviour();
	
		if (workflowHelper.isDivergentGateway(behaviour)) {
	
			applicationFlowLogs.info("Processing Divergent Gateway gatewayId={}", gatewayNode.getId());
	
			return Flux.fromIterable(nextToGateway)
					.doOnNext(mappedTask -> applicationFlowLogs.info("Resolving gateway child task={}",
							mappedTask.getNode().getId()))
					.flatMap(mappedTask -> taskAssignmentService.nextAllowedOfficeLocation(workflow, mappedTask.getNode(),
							txn.getTxnId(), service.getServiceId(), user, taskLocationUserHolderMap, service))
					.collectList().doOnNext(gatewayLocations -> applicationFlowLogs
							.info("Gateway child locations resolved={}", gatewayLocations))
					.flatMap(gatewayLocations -> {
	
						applicationFlowLogs.info("Executing Gateway MVEL gatewayId={}, map={}", gatewayNode.getId(),
								taskLocationUserHolderMap);
	
						return executeGatewayMvel(user, service, ad, txn, "", currentActionProcess, gatewayNode.getId(),
								nextToGateway, taskLocationUserHolderMap, timerDueDate).map(mvelTasks -> {
	
									applicationFlowLogs.info("Gateway selected taskIds={}",
											mvelTasks.stream().map(t -> t.getNode().getId()).toList());
	
									List<ServiceProcessFlowDTO.Data.MappedTask> filteredTasks = mvelTasks;
	
									String gatewayBehaviour = gatewayNode.getBehaviour();
	
									List<String> nextNodeIds = workflowHelper.extractNextNodeIds(nextToGateway);
	
									boolean taskSelectionPresent = selectedWorkflow != null
											&& selectedWorkflow.getTaskAttribute() != null
											&& !selectedWorkflow.getTaskAttribute().getTaskNodes().isEmpty();
	
									if ((GATEWAY_BEHAVIOUR_EXCLUSIVE_DIVERGENT.equals(gatewayBehaviour)
											|| GATEWAY_BEHAVIOUR_INCLUSIVE_DIVERGENT.equals(gatewayBehaviour))
											&& taskSelectionPresent && mvelTasks.size() == nextNodeIds.size()) {
	
										List<String> selectedTaskIds = selectedWorkflow.getTaskAttribute().getTaskNodes()
												.stream().map(ServiceProcessFlowDTO.Data.TaskNode::getTaskId).toList();
	
										applicationFlowLogs.info(
												"Gateway MVEL executed. Overriding task selection with user selection {}",
												selectedTaskIds);
	
										filteredTasks = nextToGateway.stream()
												.filter(t -> selectedTaskIds.contains(t.getNode().getId())).toList();
									}
	
									if (GATEWAY_BEHAVIOUR_EXCLUSIVE_DIVERGENT.equals(gatewayBehaviour)
											&& filteredTasks.size() != 1) {
	
										throw new SPRuntimeError("Multiple task selection not allowed",
												HttpStatus.BAD_REQUEST, txn.getTxnId());
									}
	
									applicationFlowLogs.info("Gateway final taskIds={}",
											filteredTasks.stream().map(t -> t.getNode().getId()).toList());
	
									return Map.entry(gatewayLocations, filteredTasks);
								});
					}).flatMapMany(entry -> {
	
						List<TaskAvailableOfficeLocation> gatewayLocations = entry.getKey();
	
						List<ServiceProcessFlowDTO.Data.MappedTask> filteredTasks = entry.getValue();
	
						applicationFlowLogs.info("Refreshing gateway office locations using map={}",
								taskLocationUserHolderMap);
	
						Map<String, TaskAvailableOfficeLocation> locationByTaskId = gatewayLocations.stream()
								.collect(Collectors.toMap(TaskAvailableOfficeLocation::getTaskId, Function.identity()));
	
						return Flux.fromIterable(filteredTasks).flatMap(filteredTask -> {
	
							String taskId = filteredTask.getNode().getId();
	
							TaskAvailableOfficeLocation location = locationByTaskId.get(taskId);
	
							if (location == null) {
	
								applicationFlowLogs.warn("No office location found for gateway taskId={}", taskId);
	
								return Mono.empty();
							}
	
							applicationFlowLogs.info("Refreshing location for selected gateway task={}", taskId);
	
							Integer sourceLevelCode = user.getEntityLevelId();
	
							Long sourceLocationId = user.getLocationId() != null ? user.getLocationId().longValue() : null;
	
							if (sourceLevelCode == null) {
	
								OfficeDetailsDTO sourceOfficeDetails = officeDetails.stream().filter(Objects::nonNull)
										.filter(detail -> node.getId().equals(detail.getTaskId())).findFirst().orElse(null);
	
								if (sourceOfficeDetails != null) {
	
									sourceLevelCode = sourceOfficeDetails.getOfficeLevelIds() != null
											? sourceOfficeDetails.getOfficeLevelIds().stream().findFirst().orElse(null)
											: null;
	
									sourceLocationId = sourceOfficeDetails.getAllowedOffices() != null ? sourceOfficeDetails
											.getAllowedOffices().stream().filter(Objects::nonNull)
											.map(OfficeDetailsDTO.OfficeUnitData::getOrgUnitCode).filter(Objects::nonNull)
											.map(Integer::longValue).findFirst().orElse(null) : null;
								}
							}
	
							Integer destinationLevelCode = officeDetails.stream().filter(Objects::nonNull)
									.filter(detail -> taskId.equals(detail.getTaskId()))
									.map(OfficeDetailsDTO::getOfficeLevelIds).filter(Objects::nonNull).flatMap(Set::stream)
									.findFirst().orElse(null);
	
							applicationFlowLogs.info(
									"Gateway refresh taskId={}, defaultSourceLocationId={}, defaultSourceLevelCode={}, destinationLevelCode={}",
									taskId, sourceLocationId, sourceLevelCode, destinationLevelCode);
	
							return taskAssignmentService.refreshTaskAvailableOfficeLocation(location,
									taskLocationUserHolderMap, sourceLocationId, sourceLevelCode, destinationLevelCode,
									user, ad.getApplicationId(), applicationRoutingMap,txn.getTxnId()).then(Mono.fromSupplier(() -> {
	
										applicationFlowLogs.info("Office locations refreshed for gateway taskId={} : {}",
												taskId, location);
	
										taskAvailableOfficeLocations.add(location);
	
										return location;
									}));
						}).thenMany(Flux.fromIterable(filteredTasks).map(filteredTask -> {
	
							applicationFlowLogs.info("Creating gateway process for nodeId={}",
									filteredTask.getNode().getId());
	
							return currentProcessBuilder.buildGatewayNextProcess(baseProcess, gatewayNode,
									filteredTask.getNode(), service, ad, user, now, taskAvailableOfficeLocations, workflow,
									txn);
						}));
					});
		}
	
		if (workflowHelper.isConvergentGateway(behaviour)) {
	
			applicationFlowLogs.info("Processing Convergent Gateway gatewayId={}, behaviour={}", gatewayNode.getId(),
					behaviour);
	
			return processConvergentGateway(gatewayNode, nextToGatewayData, workflow, service, ad, txn, user, now,
					currentActionProcess, baseProcess, taskAvailableOfficeLocations, taskRelationMap,
					taskLocationUserHolderMap, timerDueDate);
		}
	
		applicationFlowLogs.info("Returning normal base process for taskId={}", gatewayNode.getId());
	
		return Flux.just(baseProcess);
	
	}

    
    private Flux<CurrentProcess> processConvergentGateway(Nodes gatewayNode, ServiceProcessFlowDTO.Data nextToGatewayData, List<ServiceProcessFlowDTO.Data> workflow,
			ServiceMeta service, ApplicationDetails ad, ProcessingTxn txn, UserSessionObject user, LocalDateTime now,
			CurrentProcess currentActionProcess, CurrentProcess baseProcess,
			List<TaskAvailableOfficeLocation> taskAvailableOfficeLocations,
			Map<String, TaskRelationDTO> taskRelationMap,
			Map<String, Map<String, List<String>>> taskLocationUserHolderMap, Map<String, LocalDateTime> timerDueDate) {

        String behaviour = gatewayNode.getBehaviour();

        TaskRelationDTO taskRelation =
                taskRelationMap.get(gatewayNode.getId());

        if (taskRelation == null) {

            applicationFlowLogs.error(
                    "Task relation not found for convergent gateway={}",
                    gatewayNode.getId());

            return Flux.error(new SPRuntimeError(
                    "Task relation not found for convergent gateway",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    txn.getTxnId()));
        }

        if (GATEWAY_BEHAVIOUR_INCLUSIVE_CONVERGENT
                .equalsIgnoreCase(behaviour)) {

            return executeConvergentGatewayMvel(
                    user,
                    service,
                    ad,
                    txn,
                    "",
                    currentActionProcess,
                    gatewayNode.getId(),
                    taskLocationUserHolderMap,
                    timerDueDate)
                    .flatMapMany(mvelResponse -> {

                        if (mvelResponse.isProcessGateway()) {

                            applicationFlowLogs.info(
                                    "ICG Gateway MVEL allowed gateway processing. gatewayId={}",
                                    gatewayNode.getId());

                            return completeConvergentGateway(
                                    gatewayNode,
                                    nextToGatewayData,
                                    service,
                                    ad,
                                    txn,
                                    user,
                                    now,
                                    baseProcess,
                                    taskAvailableOfficeLocations,workflow);
                        }

                        applicationFlowLogs.info(
                                "ICG Gateway MVEL did not allow gateway processing. Falling back to IC handler. gatewayId={}",
                                gatewayNode.getId());

                        return processUsingConvergentHandler(
                                behaviour,
                                baseProcess,
                                taskRelation,
                                service,
                                txn,
                                nextToGatewayData,
                                gatewayNode,
                                ad,
                                user,
                                now,
                                taskAvailableOfficeLocations,
                                currentActionProcess,workflow,taskLocationUserHolderMap);
                    });
        }

        /*
         * PC / EC
         */
        return processUsingConvergentHandler(
                behaviour,
                baseProcess,
                taskRelation,
                service,
                txn,
                nextToGatewayData,
                gatewayNode,
                ad,
                user,
                now,
                taskAvailableOfficeLocations,
                currentActionProcess,workflow,taskLocationUserHolderMap);
    }
    
    private Mono<MvelExecutionResponse> executeConvergentGatewayMvel(
            UserSessionObject user,
            ServiceMeta service,
            ApplicationDetails applicationDetails,
            ProcessingTxn txn,
            String appData,
            CurrentProcess currentActionProcess,
            String gatewayId,
            Map<String, Map<String, List<String>>> taskLocationUserHolderMap,
            Map<String, LocalDateTime> timerDueDate) {

        applicationFlowLogs.info(
                "Executing Convergent Gateway MVEL for txnId={}, gatewayId={}, taskLocationUserHolderMap={}",
                txn.getTxnId(),
                gatewayId,
                taskLocationUserHolderMap);

        return apiClient.fetchMvelDetails(
                        user,
                        service.getServiceId(),
                        txn.getTxnId())

                .flatMapMany(Flux::fromIterable)

                .filter(m -> "GW".equalsIgnoreCase(m.getValue()))

                .doOnNext(m ->
                        applicationFlowLogs.info(
                                "Convergent Gateway MVEL candidate found mvelId={}, nodeId={}",
                                m.getMvelId(),
                                m.getNodeId()))

                .filter(m -> gatewayId.equals(m.getNodeId()))

                .doOnNext(m ->
                        applicationFlowLogs.info(
                                "Executing Convergent Gateway MVEL mvelId={}, gatewayId={}",
                                m.getMvelId(),
                                gatewayId))

                .flatMap(m ->
                        apiClient.executeMvel(
                                        m.getMvelId(),
                                        txn.getTxnId(),
                                        "",
                                        "GW",
                                        currentActionProcess.getApplicationId(),
                                        service.getServiceId(),
                                        null,
                                        appData,
                                        null,
                                        null,
                                        taskLocationUserHolderMap,
                                        null
                                )

                                .doOnNext(response ->
                                        applicationFlowLogs.info(
                                                "Convergent Gateway MVEL response txnId={}, gatewayId={}, success={}, processGateway={}, taskLocationUserHolderMap={}",
                                                txn.getTxnId(),
                                                gatewayId,
                                                response.isSuccess(),
                                                response.isProcessGateway(),
                                                response.getTaskLocationUserHolderMap()))

                                .flatMap(response -> {

                                    if (!response.isSuccess()) {

                                        applicationFlowLogs.error(
                                                "Convergent Gateway MVEL execution failed for txnId={}, gatewayId={}",
                                                txn.getTxnId(),
                                                gatewayId);

                                        return Mono.error(new SPRuntimeError(
                                                "Convergent Gateway MVEL failed",
                                                HttpStatus.BAD_GATEWAY,
                                                txn.getTxnId()));
                                    }

                                    /*
                                     * Update task location / holder map
                                     */
                                    if (response.getTaskLocationUserHolderMap() != null) {

                                        applicationFlowLogs.info(
                                                "Updating taskLocationUserHolderMap from Convergent Gateway MVEL. OldMap={}, NewMap={}",
                                                taskLocationUserHolderMap,
                                                response.getTaskLocationUserHolderMap());

                                        taskLocationUserHolderMap.clear();

                                        taskLocationUserHolderMap.putAll(
                                                response.getTaskLocationUserHolderMap());

                                        applicationFlowLogs.info(
                                                "Updated taskLocationUserHolderMap={}",
                                                taskLocationUserHolderMap);
                                    }

                                    /*
                                     * Update timer due date
                                     */
                                    if (response.getTimerDueDate() != null
                                            && !response.getTimerDueDate().isEmpty()) {

                                        timerDueDate.clear();

                                        timerDueDate.putAll(
                                                response.getTimerDueDate());
                                    }

                                    return Mono.just(response);
                                })
                )

                /*
                 * Only one Gateway MVEL should be applicable.
                 */
                .next()

                /*
                 * No MVEL configured for this gateway.
                 *
                 * Return a successful response with
                 * processGateway = false.
                 *
                 * Caller will then use the normal
                 * Inclusive Convergent Gateway handler.
                 */
                .switchIfEmpty(Mono.defer(() -> {

                    applicationFlowLogs.info(
                            "No Convergent Gateway MVEL configured for gatewayId={}, falling back to BPMN convergence",
                            gatewayId);

                    MvelExecutionResponse response =
                            new MvelExecutionResponse();

                    response.setSuccess(true);
                    response.setProcessGateway(false);

                    return Mono.just(response);
                }))

                .doOnSuccess(response ->
                        applicationFlowLogs.info(
                                "Convergent Gateway MVEL completed txnId={}, gatewayId={}, success={}, processGateway={}",
                                txn.getTxnId(),
                                gatewayId,
                                response.isSuccess(),
                                response.isProcessGateway()))

                .doOnError(ex ->
                        applicationFlowLogs.error(
                                "Convergent Gateway MVEL failed txnId={}, gatewayId={}",
                                txn.getTxnId(),
                                gatewayId,
                                ex));
    }

    private Flux<CurrentProcess> processUsingConvergentHandler(
            String behaviour,
            CurrentProcess gatewayProcess,
            TaskRelationDTO taskRelation,
            ServiceMeta service,
            ProcessingTxn txn,
            ServiceProcessFlowDTO.Data nextToGatewayData,
            ServiceProcessFlowDTO.Data.Nodes gatewayNode,
            ApplicationDetails ad,
            UserSessionObject user,
            LocalDateTime now,
            List<TaskAvailableOfficeLocation> taskAvailableOfficeLocations,
            CurrentProcess currentActionProcess,
            List<ServiceProcessFlowDTO.Data> workflow, Map<String, Map<String, List<String>>> taskLocationUserHolderMap) {

        ConvergentGatewayHandler handler =
                convergentGatewayFactory.getHandler(behaviour);

        return handler.canProceed(
                        gatewayProcess,
                        currentActionProcess,
                        taskRelation,
                        txn.getApplicationId(),
                        service.getServiceId(),
                        ad.getTenantId())

                .flatMapMany(canProceed -> {

                    if (!canProceed) {

                        applicationFlowLogs.info(
                                "Convergent gateway waiting. gatewayId={}, behaviour={}",
                                gatewayNode.getId(),
                                behaviour);

                        return Flux.empty();
                    }

                    applicationFlowLogs.info(
                            "Convergent gateway can proceed. gatewayId={}, behaviour={}",
                            gatewayNode.getId(),
                            behaviour);

                    List<ServiceProcessFlowDTO.Data.MappedTask> nextMappedTasks =
                            nextToGatewayData != null
                                    ? nextToGatewayData.getMappedTasks()
                                    : null;

                    if (nextMappedTasks == null || nextMappedTasks.isEmpty()) {

                        applicationFlowLogs.warn(
                                "No next task found after convergent gateway. gatewayId={}",
                                gatewayNode.getId());

                        return Flux.empty();
                    }

                    if (nextMappedTasks.size() > 1) {

                        applicationFlowLogs.error(
                                "Multiple next tasks found after convergent gateway. "
                                        + "gatewayId={}, nextTasks={}",
                                gatewayNode.getId(),
                                nextMappedTasks.stream()
                                        .map(t -> t.getNode().getId())
                                        .toList());

                        return Flux.error(new SPRuntimeError(
                                "Multiple next tasks are not allowed after convergent gateway",
                                HttpStatus.BAD_REQUEST,
                                txn.getTxnId()));
                    }

                    ServiceProcessFlowDTO.Data.Nodes nextNode =
                            nextMappedTasks.get(0).getNode();

                    applicationFlowLogs.info(
                            "Resolving office location for convergent gateway next task. "
                                    + "gatewayId={}, nextTaskId={}",
                            gatewayNode.getId(),
                            nextNode.getId());

                    /*
                     * Resolve office location for NEXT TASK.
                     */
                    return taskAssignmentService
                            .nextAllowedOfficeLocation(
                                    workflow,
                                    nextNode,
                                    txn.getTxnId(),
                                    service.getServiceId(),
                                    user,
                                    taskLocationUserHolderMap,
                                    service)

                            .flatMapMany(nextTaskLocation -> {

                                if (nextTaskLocation != null) {

                                    applicationFlowLogs.info(
                                            "Next task office location resolved. "
                                                    + "gatewayId={}, nextTaskId={}, location={}",
                                            gatewayNode.getId(),
                                            nextNode.getId(),
                                            nextTaskLocation);

                                    /*
                                     * Refresh location using latest holder map.
                                     */
                                    taskAssignmentService
                                            .refreshTaskAvailableOfficeLocation(
                                                    nextTaskLocation,
                                                    taskLocationUserHolderMap);

                                    taskAvailableOfficeLocations.add(
                                            nextTaskLocation);
                                }

                                return completeConvergentGateway(
                                        gatewayNode,
                                        nextToGatewayData,
                                        service,
                                        ad,
                                        txn,
                                        user,
                                        now,
                                        gatewayProcess,
                                        taskAvailableOfficeLocations,
                                        workflow);
                            });
                });
    }

	private Flux<CurrentProcess> completeConvergentGateway(
			ServiceProcessFlowDTO.Data.Nodes gatewayNode, ServiceProcessFlowDTO.Data nextNode,
			ServiceMeta service, ApplicationDetails ad,ProcessingTxn txn, UserSessionObject user, LocalDateTime now,
			CurrentProcess gatewayProcess,
			List<TaskAvailableOfficeLocation> taskAvailableOfficeLocations, List<ServiceProcessFlowDTO.Data> workflow) {

		
		applicationFlowLogs.info(
				"Convergent gateway completed. "
						+ "gatewayProcessId={}, currentTask={}, previousTask={}, actionTaken=Y",
				gatewayProcess.getProcessId(), gatewayProcess.getCurrentTask(), gatewayProcess.getPreviousTask(),
				gatewayProcess.getActionTaken());

		
		CurrentProcess nextProcess = currentProcessBuilder.buildGatewayNextProcess(gatewayProcess, gatewayNode,
				nextNode.getMappedTasks().getFirst().getNode(), service, ad, user, now, taskAvailableOfficeLocations, workflow, txn);
		
		applicationFlowLogs.info(
				"Created next task after convergent gateway. "
						+ "nextProcessId={}, currentTask={}, previousTask={}, actionTaken=N",
				nextProcess.getProcessId(), nextProcess.getCurrentTask(), nextProcess.getPreviousTask(),
				nextProcess.getActionTaken());

		return Flux.just(gatewayProcess,nextProcess);
	}

	

	private Mono<List<ServiceProcessFlowDTO.Data.MappedTask>> executeGatewayMvel(
    		UserSessionObject user,
            ServiceMeta service,
            ApplicationDetails applicationDetails,
            ProcessingTxn txn,
            String appData,
            CurrentProcess currentActionProcess,
            String gatewayId,
            List<ServiceProcessFlowDTO.Data.MappedTask> nextToGateway,
            Map<String, Map<String, List<String>>> taskLocationUserHolderMap,
            Map<String, LocalDateTime> timerDueDate

    ) {

        List<String> nextNodeIds = workflowHelper.extractNextNodeIds(nextToGateway);

        applicationFlowLogs.info(
                "Executing Gateway MVEL for txnId={}, gatewayId={}, nextNodeIds={}, taskLocationUserHolderMap={}",
                txn.getTxnId(),
                gatewayId,
                nextNodeIds,
                taskLocationUserHolderMap);

        return apiClient.fetchMvelDetails(user,service.getServiceId(),txn.getTxnId())
                .flatMapMany(Flux::fromIterable)

                .filter(m -> "GW".equalsIgnoreCase(m.getValue()))

                .doOnNext(m ->
                        applicationFlowLogs.info(
                                "Gateway MVEL candidate found mvelId={}, nodeId={}",
                                m.getMvelId(),
                                m.getNodeId()))

                .filter(m -> m.getNodeId().equals(gatewayId))

                .doOnNext(m ->
                        applicationFlowLogs.info(
                                "Executing Gateway MVEL mvelId={} for gatewayId={}",
                                m.getMvelId(),
                                gatewayId))

                .flatMap(m ->
                        apiClient.executeMvel(
                                        m.getMvelId(),
                                        txn.getTxnId(),
                                        "",
                                        "GW",
                                        currentActionProcess.getApplicationId(),
                                        service.getServiceId(),
                                        null,
                                        appData,
                                        null,
                                        null,
                                        taskLocationUserHolderMap,
                                        nextNodeIds
                                )
                                .doOnNext(response ->
                                        applicationFlowLogs.info(
                                                "Gateway MVEL response for txnId={}, gatewayId={} => success={}, nextNodeList={}, taskLocationUserHolderMap={}",
                                                txn.getTxnId(),
                                                gatewayId,
                                                response.isSuccess(),
                                                response.getNextNodeList(),
                                                response.getTaskLocationUserHolderMap()))

                                .flatMap(response -> {

                                    if (!response.isSuccess()) {

                                        applicationFlowLogs.error(
                                                "Gateway MVEL execution failed for txnId={}, gatewayId={}",
                                                txn.getTxnId(),
                                                gatewayId);

                                        return Mono.error(new SPRuntimeError(
                                                "Gateway MVEL failed",
                                                HttpStatus.BAD_GATEWAY,
                                                txn.getTxnId()
                                        ));
                                    }

                                    if (response.getTaskLocationUserHolderMap() != null) {

                                        applicationFlowLogs.info(
                                                "Updating taskLocationUserHolderMap from Gateway MVEL response. OldMap={}, NewMap={}",
                                                taskLocationUserHolderMap,
                                                response.getTaskLocationUserHolderMap());

                                        taskLocationUserHolderMap.clear();
                                        taskLocationUserHolderMap.putAll(
                                                response.getTaskLocationUserHolderMap());

                                        applicationFlowLogs.info(
                                                "Updated taskLocationUserHolderMap={}",
                                                taskLocationUserHolderMap);
                                    }
                                    
                                    if (response.getTimerDueDate()!=null && !response.getTimerDueDate().isEmpty()) {
                                    	timerDueDate.clear();
                                    	timerDueDate.putAll(response.getTimerDueDate());
                                    }

                                    List<String> filteredNodeIds =
                                            response.getNextNodeList();

                                    applicationFlowLogs.info(
                                            "Gateway MVEL selected next nodes for txnId={}, gatewayId={} => {}",
                                            txn.getTxnId(),
                                            gatewayId,
                                            filteredNodeIds);

                                    if (filteredNodeIds == null
                                            || filteredNodeIds.isEmpty()) {

                                        applicationFlowLogs.error(
                                                "No next node selected by Gateway MVEL for txnId={}, gatewayId={}",
                                                txn.getTxnId(),
                                                gatewayId);

                                        return Mono.error(new SPRuntimeError(
                                                "No valid next node from gateway",
                                                HttpStatus.BAD_REQUEST,
                                                txn.getTxnId()
                                        ));
                                    }

                                    List<ServiceProcessFlowDTO.Data.MappedTask> filteredTasks =
                                            nextToGateway.stream()
                                                    .filter(task ->
                                                            filteredNodeIds.contains(
                                                                    task.getNode().getId()))
                                                    .toList();

                                    applicationFlowLogs.info(
                                            "Filtered gateway tasks for txnId={}, gatewayId={} => {}",
                                            txn.getTxnId(),
                                            gatewayId,
                                            filteredTasks.stream()
                                                    .map(t -> t.getNode().getId())
                                                    .toList());

                                    return Mono.just(filteredTasks);
                                })
                )

                .next()

                .switchIfEmpty(Mono.defer(() -> {

                    applicationFlowLogs.warn(
                            "No Gateway MVEL configured for gatewayId={}, returning all next tasks={}",
                            gatewayId,
                            nextNodeIds);

                    return Mono.just(nextToGateway);
                }))

                .doOnSuccess(result ->
                        applicationFlowLogs.info(
                                "Gateway processing completed for txnId={}, gatewayId={}, finalTasks={}",
                                txn.getTxnId(),
                                gatewayId,
                                result.stream()
                                        .map(t -> t.getNode().getId())
                                        .toList()))

                .doOnError(ex ->
                        applicationFlowLogs.error(
                                "Gateway processing failed for txnId={}, gatewayId={}",
                                txn.getTxnId(),
                                gatewayId,
                                ex));
    }
}