package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.FALLBACK_ACTION_NO;
import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_EXCLUSIVE_CONVERGENT;
import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_EXCLUSIVE_DIVERGENT;
import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_INCLUSIVE_CONVERGENT;
import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_INCLUSIVE_DIVERGENT;
import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_PARALLEL_CONVERGENT;
import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_PARALLEL_DIVERGENT;
import static com.serviceplus.form.validation.utility.ApplicationConstants.SERVICE_WORKFLOW_REDIS_KEY_APPENDER;
import static com.serviceplus.form.validation.utility.ApplicationConstants.TYPE_GATEWAY;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.google.gson.reflect.TypeToken;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.InboxKafka;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceWorkFlow;
import com.serviceplus.form.validation.dto.TaskAvailableOfficeLocation;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.dto.WorkflowAssignmentDTO;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class WorkflowService {

    @Autowired
    private CurrentProcessRepository currentProcessRepository;

    @Autowired
    private ReactiveApiClient apiClient;

    @Autowired
    private RedisService redis;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    public void saveAndFlush(CurrentProcess c){
        currentProcessRepository.save(c);
    }

    public Mono<?> generateNextWorkflow(ApplicationDetails ad, ProcessingTxn savedLog, ServiceMeta service, UserSessionObject user, CurrentProcess cp) {
        final String REDIS_KEY =SERVICE_WORKFLOW_REDIS_KEY_APPENDER.concat("_").concat(service.getServiceId().toString());

        Mono<Object> redisData = redis.fetch(
                REDIS_KEY, new TypeToken<ServiceWorkFlow>(){}.getType()
        );

        return redisData
                .switchIfEmpty(
                        apiClient.fetchProcessFlow(service.getBaseServiceId(), user,
                                        savedLog.getApplicationId(), service.getTaskId(), service.getServiceId(),savedLog.getTxnId())
                                .switchIfEmpty(Mono.error(new SPRuntimeError("Workflow Exception [ERR - 01]", HttpStatus.FAILED_DEPENDENCY,savedLog.getTxnId())))
                                .flatMap(Mono::just)
                )
                .flatMap(response -> generate((ServiceWorkFlow) response, ad, savedLog, service, user,cp))
                .onErrorResume(ex -> {
                    ex.printStackTrace();
                    return Mono.error(new SPRuntimeError("Workflow Error [ERR -01]",HttpStatus.INTERNAL_SERVER_ERROR,savedLog.getTxnId()));
                });
    }

    private Mono<?> generate(ServiceWorkFlow response, ApplicationDetails ad, ProcessingTxn savedLog,
                             ServiceMeta service, UserSessionObject user, CurrentProcess cp) {
        List<ServiceWorkFlow.Data> wf =  response.getData();
        String currentTask = service.getTaskId();

        applicationFlowLogs.info("Generating workflow for txnId {} currentTask {}",savedLog.getTxnId(),currentTask);

        ServiceWorkFlow.Data data = fetchNode(wf, currentTask);

        if(data != null) {
            applicationFlowLogs.info("Generating workflow for txnId {} currentTask {} nextNode {}"
                    ,savedLog.getTxnId(),currentTask,data.toString());

            return calculateNextWorkflow(data.getNode(), data, service, ad, savedLog, user, wf,cp);
        }

        return Mono.empty();
    }

    private Mono<InboxKafka> calculateNextWorkflow(ServiceWorkFlow.Data.Nodes node, ServiceWorkFlow.Data data,
                                                   ServiceMeta service, ApplicationDetails ad, ProcessingTxn txn, UserSessionObject user,
                                                   List<ServiceWorkFlow.Data> wf, CurrentProcess currentActionProcess) {

        LocalDateTime now = LocalDateTime.now();

        List<TaskAvailableOfficeLocation> taskAvailableOfficeLocations = new ArrayList<>();
        List<CurrentProcess> pList = new ArrayList<>();

        Map<String, Map<String, List<String>>> taskLocationUserHolderMap = new HashMap<>();

        applicationFlowLogs.info("calculateNextWorkflow started txnId={}, currentNode={}, mappedTasks={}",
                txn.getTxnId(), node.getId(), data.getMappedTasks().size());

        return Flux.fromIterable(data.getMappedTasks())

                .concatMap(task -> {

                    ServiceWorkFlow.Data.Nodes next = task.getNode();

                    applicationFlowLogs.info("Processing taskId={}, taskType={}, txnId={}", next.getId(),
                            next.getType(), txn.getTxnId());

                    return nextAllowedOfficeLocation(wf, next, txn.getTxnId(), service.getServiceId(), user,
                            taskLocationUserHolderMap)

                            .flatMap(nextAllowedOfficeLocation -> {

                                applicationFlowLogs.info("nextAllowedOfficeLocation resolved for taskId={} : {}",
                                        next.getId(), nextAllowedOfficeLocation);

                                taskAvailableOfficeLocations.add(nextAllowedOfficeLocation);

                                applicationFlowLogs.info("Executing After Task MVEL for taskId={}, currentMap={}",
                                        next.getId(), taskLocationUserHolderMap);

                                return executeAfterTaskMvel(service, ad, txn, "", currentActionProcess, next.getId(),
                                        taskLocationUserHolderMap)

                                        .then(Mono.defer(() -> {

                                            applicationFlowLogs.info(
                                                    "After Task MVEL completed for taskId={}, updatedMap={}",
                                                    next.getId(), taskLocationUserHolderMap);

                                            refreshTaskAvailableOfficeLocation(nextAllowedOfficeLocation,
                                                    taskLocationUserHolderMap);

                                            applicationFlowLogs.info("Office locations refreshed for taskId={} : {}",
                                                    next.getId(), nextAllowedOfficeLocation);

                                            CurrentProcess baseProcess = buildBaseProcess(currentActionProcess, node,
                                                    next, service, ad, user, now, taskAvailableOfficeLocations, wf,
                                                    txn);

                                            applicationFlowLogs.info("Base process created for taskId={}, process={}",
                                                    next.getId(), baseProcess);

                                            pList.add(baseProcess);

                                            if (TYPE_GATEWAY.equals(next.getType())) {

                                                applicationFlowLogs.info(
                                                        "Gateway encountered gatewayId={}, behaviour={}", next.getId(),
                                                        next.getBehaviour());

                                                ServiceWorkFlow.Data nextToGatewayData = fetchNode(wf, next.getId());

                                                List<ServiceWorkFlow.Data.MappedTask> nextToGateway = nextToGatewayData
                                                        .getMappedTasks();

                                                applicationFlowLogs.info("Gateway next nodes={}",
                                                        nextToGateway.stream().map(t -> t.getNode().getId()).toList());

                                                String behaviour = next.getBehaviour();

                                                if (isDivergentGateway(behaviour)) {

                                                    applicationFlowLogs.info(
                                                            "Processing Divergent Gateway gatewayId={}", next.getId());

                                                    return Flux.fromIterable(nextToGateway)

                                                            .doOnNext(mappedTask -> applicationFlowLogs.info(
                                                                    "Resolving gateway child task={}",
                                                                    mappedTask.getNode().getId()))

                                                            .flatMap(mappedTask -> nextAllowedOfficeLocation(wf,
                                                                    mappedTask.getNode(), txn.getTxnId(),
                                                                    service.getServiceId(), user,
                                                                    taskLocationUserHolderMap))

                                                            .collectList()

                                                            .doOnNext(gatewayLocations -> applicationFlowLogs.info(
                                                                    "Gateway child locations resolved={}",
                                                                    gatewayLocations))

                                                            .flatMap(gatewayLocations -> {

                                                                applicationFlowLogs.info(
                                                                        "Executing Gateway MVEL gatewayId={}, map={}",
                                                                        next.getId(), taskLocationUserHolderMap);

                                                                return executeGatewayMvel(service, ad, txn, "",
                                                                        currentActionProcess, next.getId(),
                                                                        nextToGateway, taskLocationUserHolderMap)

                                                                        .map(filteredTasks -> {

                                                                            applicationFlowLogs.info(
                                                                                    "Gateway selected taskIds={}",
                                                                                    filteredTasks.stream().map(
                                                                                                    t -> t.getNode().getId())
                                                                                            .toList());

                                                                            return Map.entry(gatewayLocations,
                                                                                    filteredTasks);
                                                                        });
                                                            })

                                                            .flatMapMany(entry -> {

                                                                List<TaskAvailableOfficeLocation> gatewayLocations = entry
                                                                        .getKey();

                                                                List<ServiceWorkFlow.Data.MappedTask> filteredTasks = entry
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

                                                                        refreshTaskAvailableOfficeLocation(location,
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

                                                                return buildGatewayNextProcess(baseProcess, next,
                                                                        filteredTask.getNode(), service, ad, user, now,
                                                                        taskAvailableOfficeLocations, wf, txn);
                                                            }).next();
                                                }

                                                if (isConvergentGateway(behaviour)) {

                                                    applicationFlowLogs.info(
                                                            "Processing Convergent Gateway gatewayId={}", next.getId());

                                                    ServiceWorkFlow.Data.Nodes nextNode = nextToGatewayData.getNode();

                                                    CurrentProcess cp = buildGatewayNextProcess(baseProcess, next,
                                                            nextNode, service, ad, user, now,
                                                            taskAvailableOfficeLocations, wf, txn);

                                                    applicationFlowLogs.info(
                                                            "Convergent Gateway produced process for nodeId={}",
                                                            nextNode.getId());

                                                    return Mono.just(cp);
                                                }
                                            }

                                            applicationFlowLogs.info("Returning normal base process for taskId={}",
                                                    next.getId());

                                            return Mono.just(baseProcess);
                                        }));
                            });

                })

                .collectList()

                .map(processList -> {

                    applicationFlowLogs.info("Workflow processing completed. Generated process count={}",
                            processList.size());

                    processList.add(currentActionProcess);

                    if (!pList.isEmpty()) {
                        processList.add(pList.getFirst());
                    }

                    InboxKafka inboxKafkaDto = new InboxKafka();
                    inboxKafkaDto.setProcessList(processList);
                    inboxKafkaDto.setOfficeDetails(taskAvailableOfficeLocations);
                    inboxKafkaDto.setServiceName(service.getServiceName());
                    inboxKafkaDto.setOfficeDetails(taskAvailableOfficeLocations);
                    inboxKafkaDto.setServiceName(service.getServiceName());
                    inboxKafkaDto.setAppliedBy(ad.getBeneficiaryId());
                    inboxKafkaDto.setBeneficiaryName(ad.getBeneficiaryName());
                    inboxKafkaDto.setApplyDate(ad.getApplyDate());

                    applicationFlowLogs.info("InboxKafka prepared txnId={}, processCount={}, officeLocationCount={}",
                            txn.getTxnId(), inboxKafkaDto.getProcessList().size(),
                            inboxKafkaDto.getOfficeDetails().size());

                    applicationFlowLogs.info("Final taskLocationUserHolderMap={}", taskLocationUserHolderMap);

                    return inboxKafkaDto;
                })

                .doOnError(
                        ex -> applicationFlowLogs.error("Error in calculateNextWorkflow txnId={}", txn.getTxnId(), ex));
    }

    private CurrentProcess buildBaseProcess(CurrentProcess currentActionProcess,ServiceWorkFlow.Data.Nodes currentNode,ServiceWorkFlow.Data.Nodes currentTask,
                                            ServiceMeta service,ApplicationDetails ad,UserSessionObject user,
                                            LocalDateTime now,List<TaskAvailableOfficeLocation> taskAvailableOfficeLocations,List<ServiceWorkFlow.Data> wf,
                                            ProcessingTxn txn
    ) {

        CurrentProcess cp = new CurrentProcess();

        cp.setPreviousProcessId(currentActionProcess.getProcessId());
        cp.setCurrentTask(currentTask.getId());
        cp.setCurrentTaskName(currentTask.getName());
        cp.setPreviousTask(currentNode.getId());
        cp.setPreviousTaskName(currentNode.getName());
        cp.setServiceId(service.getServiceId());
        cp.setApplicationId(ad.getApplicationId());
        cp.setTenantId(user.getTenantId());
        cp.setBaseServiceId(service.getBaseServiceId());
        cp.setFormId(currentTask.getFormId());
        if(TYPE_GATEWAY.equals(currentTask.getType())){
            cp.setActionTaken("Y");
            cp.setProcessId(createUniqueId());
            cp.setActionOn(now);
            cp.setGateway(Boolean.TRUE);
        }else{
            cp.setInitiatedOn(now);
            cp.setProcessId(createUniqueId());
        }
        cp.setProcessId(createUniqueId());
        cp.setNewEntity(true);
        cp.setActionCode(FALLBACK_ACTION_NO);

        return cp;
    }
    private CurrentProcess buildGatewayNextProcess(
            CurrentProcess parent,
            ServiceWorkFlow.Data.Nodes gatewayNode,
            ServiceWorkFlow.Data.Nodes nextNode,
            ServiceMeta service,
            ApplicationDetails ad,
            UserSessionObject user,
            LocalDateTime now,
            List<TaskAvailableOfficeLocation> taskAvailableOfficeLocations,
            List<ServiceWorkFlow.Data> wf,
            ProcessingTxn txn
    ) {

        CurrentProcess cp = new CurrentProcess();

        cp.setPreviousProcessId(parent.getProcessId());
        cp.setCurrentTask(nextNode.getId());
        cp.setCurrentTaskName(nextNode.getName());
        cp.setPreviousTask(gatewayNode.getId());
        cp.setPreviousTaskName(gatewayNode.getName());
        cp.setServiceId(service.getServiceId());
        cp.setApplicationId(ad.getApplicationId());
        cp.setTenantId(user.getTenantId());
        cp.setBaseServiceId(service.getBaseServiceId());
        cp.setFormId(nextNode.getFormId());
        cp.setInitiatedOn(now);
        cp.setProcessId(createUniqueId());
        cp.setNewEntity(true);
        cp.setActionTaken("N");
        cp.setActionCode(FALLBACK_ACTION_NO);

        return cp;
    }
    private boolean isDivergentGateway(String behaviour) {
        return behaviour.equals(GATEWAY_BEHAVIOUR_EXCLUSIVE_DIVERGENT)
                || behaviour.equals(GATEWAY_BEHAVIOUR_INCLUSIVE_DIVERGENT)
                || behaviour.equals(GATEWAY_BEHAVIOUR_PARALLEL_DIVERGENT);
    }

    private boolean isConvergentGateway(String behaviour) {
        return behaviour.equals(GATEWAY_BEHAVIOUR_EXCLUSIVE_CONVERGENT)
                || behaviour.equals(GATEWAY_BEHAVIOUR_INCLUSIVE_CONVERGENT)
                || behaviour.equals(GATEWAY_BEHAVIOUR_PARALLEL_CONVERGENT);
    }

    private ServiceWorkFlow.Data fetchNode(List<ServiceWorkFlow.Data> wf , String task){
        for(ServiceWorkFlow.Data data : wf){
            ServiceWorkFlow.Data.Nodes nodes = data.getNode();
            if(task.equals(nodes.getId())){
                return data;
            }
        }
        return null;
    }

    private Mono<List<ServiceWorkFlow.Data.MappedTask>> executeGatewayMvel(

            ServiceMeta service,
            ApplicationDetails applicationDetails,
            ProcessingTxn txn,
            String appData,
            CurrentProcess currentActionProcess,
            String gatewayId,
            List<ServiceWorkFlow.Data.MappedTask> nextToGateway,
            Map<String, Map<String, List<String>>> taskLocationUserHolderMap

    ) {

        List<String> nextNodeIds = extractNextNodeIds(nextToGateway);

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

                                    List<ServiceWorkFlow.Data.MappedTask> filteredTasks =
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
    private List<String> extractNextNodeIds(List<ServiceWorkFlow.Data.MappedTask> mappedTasks) {

        return mappedTasks.stream()
                .map(m -> m.getNode().getId())
                .filter(Objects::nonNull)
                .toList();
    }

    private Mono<TaskAvailableOfficeLocation> nextAllowedOfficeLocation(List<ServiceWorkFlow.Data> wf,
                                                                        ServiceWorkFlow.Data.Nodes next, String txnId, Integer serviceId, UserSessionObject user,
                                                                        Map<String, Map<String, List<String>>> taskLocationHolderMap) {

        applicationFlowLogs.info("Calculating nextAllowedOfficeLocation for txnId={}, serviceId={}, taskId={}", txnId,
                serviceId, next.getId());

        TaskAvailableOfficeLocation location = new TaskAvailableOfficeLocation();
        location.setTaskId(next.getId());

        List<ServiceMeta.AvailableApplyLocations> allowedOffices = wf.stream().filter(d -> d.getNode() != null)
                .filter(d -> next.getId().equals(d.getNode().getId())).map(ServiceWorkFlow.Data::getAllowedOffices)
                .filter(Objects::nonNull).findFirst().orElseGet(List::of);

        applicationFlowLogs.info("Allowed offices found for txnId={}, taskId={} : {}", txnId, next.getId(),
                allowedOffices);

        location.setAllowedOffices(allowedOffices);

        return Flux.fromIterable(allowedOffices)

                .doOnNext(office -> applicationFlowLogs.info(
                        "Fetching workflow assignments for txnId={}, taskId={}, locationId={}", txnId, next.getId(),
                        office.getLocationId()))

                .flatMap(office -> apiClient.fetchWorkflowAssignments(serviceId, next.getId(),
                        office.getLocationId().toString(), user, txnId))

                .doOnNext(assignments -> applicationFlowLogs.info(
                        "Workflow assignments received for txnId={}, taskId={} : {}", txnId, next.getId(), assignments))

                .flatMapIterable(assignments -> assignments)

                .doOnNext(assignment -> applicationFlowLogs.info("Assignment -> locationId={}, holderId={}",
                        assignment.getLocationId(), assignment.getHolderId()))

                .collectList()

                .map(assignments -> {

                    applicationFlowLogs.info("Total assignments fetched for txnId={}, taskId={} : {}", txnId,
                            next.getId(), assignments.size());

                    Map<String, List<String>> locationHolderMap = taskLocationHolderMap.computeIfAbsent(next.getId(),
                            k -> new HashMap<>());

                    assignments.stream().filter(a -> a.getLocationId() != null).forEach(a -> {

                        applicationFlowLogs.info("Adding holderId={} for locationId={} taskId={}", a.getHolderId(),
                                a.getLocationId(), next.getId());

                        locationHolderMap.computeIfAbsent(a.getLocationId(), k -> new ArrayList<>())
                                .add(a.getHolderId());
                    });

                    applicationFlowLogs.info("Location holder map before duplicate removal for taskId={} : {}",
                            next.getId(), locationHolderMap);

                    locationHolderMap.replaceAll(
                            (locationId, holders) -> holders.stream().filter(Objects::nonNull).distinct().toList());

                    applicationFlowLogs.info("Location holder map after duplicate removal for taskId={} : {}",
                            next.getId(), locationHolderMap);

                    location.getAllowedOffices().forEach(office -> {

                        String officeId = office.getLocationId().toString();

                        List<String> holderIds = locationHolderMap.getOrDefault(officeId, List.of());

                        office.setHolderIds(holderIds);

                        applicationFlowLogs.info("Mapped office locationId={} with holderIds={} for taskId={}",
                                officeId, holderIds, next.getId());
                    });

                    applicationFlowLogs.info("Final taskLocationHolderMap for txnId={}, taskId={} : {}", txnId,
                            next.getId(), taskLocationHolderMap);

                    applicationFlowLogs.info("Final TaskAvailableOfficeLocation for txnId={}, taskId={} : {}", txnId,
                            next.getId(), location);

                    return location;
                })

                .defaultIfEmpty(location)

                .doOnSuccess(result -> applicationFlowLogs.info(
                        "Completed nextAllowedOfficeLocation for txnId={}, taskId={}, result={}", txnId, next.getId(),
                        result))

                .onErrorResume(ex -> {

                    applicationFlowLogs.error("Error fetching workflow assignments for txnId={}, taskId={}", txnId,
                            next.getId(), ex);

                    return Mono.just(location);
                });
    }

    private Mono<Void> executeAfterTaskMvel(ServiceMeta service, ApplicationDetails applicationDetails, ProcessingTxn txn, String appData,
                                            CurrentProcess currentActionProcess, String taskId, Map<String, Map<String,List<String>>> taskLocationUserHolderMap
    ) {

        return apiClient.fetchMvelDetails(service.getServiceId())
                .flatMapMany(Flux::fromIterable)
                .filter(m -> "AT".equalsIgnoreCase(m.getValue())).filter(m -> taskId.equals(m.getNodeId()))
                .flatMap(m -> apiClient
                        .executeMvel(m.getMvelId(), txn.getTxnId(), "", "AT", currentActionProcess.getApplicationId(),
                                service.getServiceId(), null, appData, null, null, taskLocationUserHolderMap,null)
                        .flatMap(response -> {
                            if (!response.isSuccess()) {
                                return Mono.error(new SPRuntimeError("After Task MVEL failed", HttpStatus.BAD_GATEWAY,
                                        txn.getTxnId()));
                            }
                            if (response.getTaskLocationUserHolderMap() != null) {
                                taskLocationUserHolderMap.clear();
                                taskLocationUserHolderMap.putAll(response.getTaskLocationUserHolderMap());
                            }
                            return Mono.empty();
                        }))
                .then();
    }

    private void refreshTaskAvailableOfficeLocation(TaskAvailableOfficeLocation officeLocation,
                                                    Map<String, Map<String, List<String>>> taskLocationUserHolderMap) {

        applicationFlowLogs.info("Refreshing office locations for taskId={}, taskLocationUserHolderMap={}",
                officeLocation.getTaskId(), taskLocationUserHolderMap);

        Map<String, List<String>> locationHolderMap = taskLocationUserHolderMap.getOrDefault(officeLocation.getTaskId(),
                Map.of());

        applicationFlowLogs.info("Location holder map for taskId {} : {}", officeLocation.getTaskId(),
                locationHolderMap);

        List<ServiceMeta.AvailableApplyLocations> filteredOffices = officeLocation.getAllowedOffices().stream()
                .filter(office -> {

                    String locationId = String.valueOf(office.getLocationId());

                    List<String> holderIds = locationHolderMap.get(locationId);

                    applicationFlowLogs.info("Evaluating taskId={}, locationId={}, holderIds={}",
                            officeLocation.getTaskId(), locationId, holderIds);

                    if (holderIds == null || holderIds.isEmpty()) {

                        applicationFlowLogs.info("Removing locationId={} for taskId={} because no holders found",
                                locationId, officeLocation.getTaskId());

                        return false;
                    }

                    office.setHolderIds(holderIds);

                    applicationFlowLogs.info("Retaining locationId={} for taskId={} with holderIds={}", locationId,
                            officeLocation.getTaskId(), holderIds);

                    return true;
                }).toList();

        applicationFlowLogs.info("Filtered offices for taskId={} before={}, after={}", officeLocation.getTaskId(),
                officeLocation.getAllowedOffices().size(), filteredOffices.size());

        officeLocation.setAllowedOffices(new ArrayList<>(filteredOffices));

        applicationFlowLogs.info("Final office locations for taskId={} : {}", officeLocation.getTaskId(),
                officeLocation.getAllowedOffices());
    }

}