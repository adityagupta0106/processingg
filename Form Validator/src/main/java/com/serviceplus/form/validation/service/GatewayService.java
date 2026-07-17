package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.Helpers.CurrentProcessBuilder;
import com.serviceplus.form.validation.Helpers.WorkflowHelper;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.TaskAvailableOfficeLocation;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.serviceplus.form.validation.utility.ApplicationConstants.*;

@Service
public class GatewayService {

    private final ReactiveApiClient apiClient;

    private final WorkflowHelper workflowHelper;

    private final TaskAssignmentService taskAssignmentService;

    private final CurrentProcessBuilder currentProcessBuilder;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    public GatewayService(ReactiveApiClient apiClient, WorkflowHelper workflowHelper, TaskAssignmentService taskAssignmentService, CurrentProcessBuilder currentProcessBuilder) {
        this.apiClient = apiClient;
        this.workflowHelper = workflowHelper;
        this.taskAssignmentService = taskAssignmentService;
        this.currentProcessBuilder = currentProcessBuilder;
    }

    public Mono<CurrentProcess> processGateway(
            ServiceProcessFlowDTO.Data.Nodes node,
            ServiceProcessFlowDTO.Data.Nodes gatewayNode,
            List<ServiceProcessFlowDTO.Data> wf,
            ServiceMeta service,
            ApplicationDetails ad,
            ProcessingTxn txn,
            UserSessionObject user,
            LocalDateTime now,
            CurrentProcess currentActionProcess,
            CurrentProcess baseProcess,
            List<TaskAvailableOfficeLocation> taskAvailableOfficeLocations,
            Map<String, Map<String,List<String>>> taskLocationUserHolderMap,
            ServiceProcessFlowDTO.Data.WorkflowElementData selectedWorkflow){


            applicationFlowLogs.info(
                    "Gateway encountered gatewayId={}, behaviour={}", gatewayNode.getId(),
                    gatewayNode.getBehaviour());

            ServiceProcessFlowDTO.Data nextToGatewayData = workflowHelper.fetchNode(wf, gatewayNode.getId());

            List<ServiceProcessFlowDTO.Data.MappedTask> nextToGateway = nextToGatewayData
                    .getMappedTasks();

            applicationFlowLogs.info("Gateway next nodes={}",
                    nextToGateway.stream().map(t -> t.getNode().getId()).toList());

            String behaviour = gatewayNode.getBehaviour();

            if (workflowHelper.isDivergentGateway(behaviour)) {

                applicationFlowLogs.info(
                        "Processing Divergent Gateway gatewayId={}", gatewayNode.getId());

                return Flux.fromIterable(nextToGateway)

                        .doOnNext(mappedTask -> applicationFlowLogs.info("Resolving gateway child task={}", mappedTask.getNode().getId()))

                        .flatMap(mappedTask -> taskAssignmentService.nextAllowedOfficeLocation(wf,
                                mappedTask.getNode(), txn.getTxnId(),
                                service.getServiceId(), user,
                                taskLocationUserHolderMap,service))

                        .collectList().doOnNext(gatewayLocations -> applicationFlowLogs.info("Gateway child locations resolved={}", gatewayLocations))

                        .flatMap(gatewayLocations -> {

                            applicationFlowLogs.info("Executing Gateway MVEL gatewayId={}, map={}", gatewayNode.getId(), taskLocationUserHolderMap);


                            return executeGatewayMvel(service, ad, txn, "",
                                    currentActionProcess, gatewayNode.getId(),
                                    nextToGateway, taskLocationUserHolderMap)

                                    .map(mvelTasks -> {

                                        applicationFlowLogs.info("Gateway selected taskIds={}", mvelTasks.stream().map(t -> t.getNode().getId()).toList());

                                        List<ServiceProcessFlowDTO.Data.MappedTask> filteredTasks = mvelTasks;

                                        String gatewayBehaviour = gatewayNode.getBehaviour();
                                        List<String> nextNodeIds = workflowHelper.extractNextNodeIds(nextToGateway);

                                        boolean taskSelectionPresent =
                                                selectedWorkflow != null
                                                        && selectedWorkflow.getTaskAttribute() != null
                                                        && !selectedWorkflow.getTaskAttribute().getTaskNodes().isEmpty();

                                        if ((GATEWAY_BEHAVIOUR_EXCLUSIVE_DIVERGENT.equals(gatewayBehaviour)
                                                || GATEWAY_BEHAVIOUR_INCLUSIVE_DIVERGENT.equals(gatewayBehaviour))
                                                && taskSelectionPresent && mvelTasks.size() == nextNodeIds.size()) {

                                            List<String> selectedTaskIds = selectedWorkflow.getTaskAttribute()
                                                    .getTaskNodes()
                                                    .stream()
                                                    .map(ServiceProcessFlowDTO.Data.TaskNode::getTaskId)
                                                    .toList();

                                            applicationFlowLogs.info(
                                                    "Gateway MVEL executed. Overriding task selection with user selection {}",
                                                    selectedTaskIds);

                                            filteredTasks = nextToGateway.stream()
                                                    .filter(t -> selectedTaskIds.contains(t.getNode().getId()))
                                                    .toList();
                                        }

                                        if ((GATEWAY_BEHAVIOUR_EXCLUSIVE_DIVERGENT.equals(gatewayBehaviour))
                                                        && (filteredTasks.size() != 1)){
                                            throw new SPRuntimeError("Multiple task selection not allowed",HttpStatus.BAD_REQUEST,txn.getTxnId());
                                        }

                                        applicationFlowLogs.info("Gateway final taskIds={}", filteredTasks.stream().map(t -> t.getNode().getId()).toList());

                                        return Map.entry(gatewayLocations, filteredTasks);
                                    });
                        })

                        .flatMapMany(entry -> {

                            List<TaskAvailableOfficeLocation> gatewayLocations = entry
                                    .getKey();

                            List<ServiceProcessFlowDTO.Data.MappedTask> filteredTasks = entry
                                    .getValue();

                            applicationFlowLogs.info(
                                    "Refreshing gateway office locations using map={}",
                                    taskLocationUserHolderMap);

                            Map<String, TaskAvailableOfficeLocation> locationByTaskId = gatewayLocations
                                    .stream()
                                    .collect(Collectors.toMap(
                                            TaskAvailableOfficeLocation::getTaskId,
                                            Function.identity()));

                            filteredTasks.forEach(filteredTask -> {

                                TaskAvailableOfficeLocation location = locationByTaskId
                                        .get(filteredTask.getNode().getId());

                                if (location != null) {

                                    applicationFlowLogs.info(
                                            "Refreshing location for selected gateway task={}",
                                            filteredTask.getNode().getId());

                                    taskAssignmentService.refreshTaskAvailableOfficeLocation(location,
                                            taskLocationUserHolderMap);

                                    taskAvailableOfficeLocations.add(location);
                                }
                            });

                            return Flux.fromIterable(filteredTasks);
                        })

                        .map(filteredTask -> {

                            applicationFlowLogs.info(
                                    "Creating gateway process for nodeId={}",
                                    filteredTask.getNode().getId());

                            return currentProcessBuilder.buildGatewayNextProcess(baseProcess, gatewayNode,
                                    filteredTask.getNode(), service, ad, user, now,
                                    taskAvailableOfficeLocations, wf, txn);
                        }).next();
            }

            if (workflowHelper.isConvergentGateway(behaviour)) {

                applicationFlowLogs.info(
                        "Processing Convergent Gateway gatewayId={}", gatewayNode.getId());

                ServiceProcessFlowDTO.Data.Nodes nextNode = nextToGatewayData.getNode();

                CurrentProcess cp = currentProcessBuilder.buildGatewayNextProcess(baseProcess, gatewayNode,
                        nextNode, service, ad, user, now,
                        taskAvailableOfficeLocations, wf, txn);

                applicationFlowLogs.info(
                        "Convergent Gateway produced process for nodeId={}",
                        nextNode.getId());

                return Mono.just(cp);
            }

        applicationFlowLogs.info("Returning normal base process for taskId={}",
                gatewayNode.getId());

        return Mono.just(baseProcess);
    }

    private Mono<List<ServiceProcessFlowDTO.Data.MappedTask>> executeGatewayMvel(

            ServiceMeta service,
            ApplicationDetails applicationDetails,
            ProcessingTxn txn,
            String appData,
            CurrentProcess currentActionProcess,
            String gatewayId,
            List<ServiceProcessFlowDTO.Data.MappedTask> nextToGateway,
            Map<String, Map<String, List<String>>> taskLocationUserHolderMap

    ) {

        List<String> nextNodeIds = workflowHelper.extractNextNodeIds(nextToGateway);

        applicationFlowLogs.info(
                "Executing Gateway MVEL for txnId={}, gatewayId={}, nextNodeIds={}, taskLocationUserHolderMap={}",
                txn.getTxnId(),
                gatewayId,
                nextNodeIds,
                taskLocationUserHolderMap);

        return apiClient.fetchMvelDetails(service.getServiceId())
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