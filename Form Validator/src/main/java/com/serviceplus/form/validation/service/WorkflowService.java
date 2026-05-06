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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    private Mono<InboxKafka> calculateNextWorkflow(
    		ServiceWorkFlow.Data.Nodes node, ServiceWorkFlow.Data data,
    		ServiceMeta service, ApplicationDetails ad, ProcessingTxn txn,
    		UserSessionObject user, List<ServiceWorkFlow.Data> wf, CurrentProcess currentActionProcess) {

        LocalDateTime now = LocalDateTime.now();
        List<TaskAvailableOfficeLocation> taskAvailableOfficeLocations = new ArrayList<>();
        List<CurrentProcess> pList = new ArrayList<>();
        return Flux.fromIterable(data.getMappedTasks())

                .flatMap(task -> {

                    ServiceWorkFlow.Data.Nodes next = task.getNode();
                    CurrentProcess baseProcess = buildBaseProcess(currentActionProcess,node,next,service,ad,user,now,taskAvailableOfficeLocations,wf,txn);
                    pList.add(baseProcess);
                    
                    if (TYPE_GATEWAY.equals(next.getType())) {

                        ServiceWorkFlow.Data nextToGatewayData = fetchNode(wf, next.getId());

                        List<ServiceWorkFlow.Data.MappedTask> nextToGateway =
                                nextToGatewayData.getMappedTasks();

                        String behaviour = next.getBehaviour();
                        if (isDivergentGateway(behaviour)) {
                            return executeGatewayMvel(service,ad,txn,"",currentActionProcess,next.getId(),nextToGateway)
                            .flatMapMany(Flux::fromIterable)
                            .map(filteredTask -> buildGatewayNextProcess(baseProcess,next,filteredTask.getNode(),service,ad, user,now,taskAvailableOfficeLocations,wf,txn));
                        }
                        else if (isConvergentGateway(behaviour)) {
                            ServiceWorkFlow.Data.Nodes nextNode = nextToGatewayData.getNode();
                            CurrentProcess cp = buildGatewayNextProcess(baseProcess,next,nextNode,service,ad,user,now,taskAvailableOfficeLocations,wf,txn);
                            //need to check condition.
                            return Mono.just(cp);
                        }
                    }
                    return Mono.just(baseProcess);
                })
                .collectList()
                .map(processList -> {
                    processList.add(currentActionProcess);
                    processList.add(pList.getFirst());
                    InboxKafka inboxKafkaDto = new InboxKafka();
                    inboxKafkaDto.setLocationId(service.getSelectedLocationByUser());
                    inboxKafkaDto.setLocationName(service.getSelectedLocationNameByUser());
                    inboxKafkaDto.setProcessList(processList);
                    inboxKafkaDto.setOfficeDetails(taskAvailableOfficeLocations);
                    inboxKafkaDto.setServiceName(service.getServiceName());

                    return inboxKafkaDto;
                });
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
        }else{
    	  cp.setInitiatedOn(now);
    	  cp.setProcessId(createUniqueId());
    	  taskAvailableOfficeLocations.add(nextAllowedOfficeLocation(wf,currentTask,txn.getTxnId()));
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
        taskAvailableOfficeLocations.add(nextAllowedOfficeLocation(wf,nextNode,txn.getTxnId()));

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
            CurrentProcess currentActionProcess ,
            String gatewayId,
            List<ServiceWorkFlow.Data.MappedTask> nextToGateway

    ) {
        List<String> nextNodeIds = extractNextNodeIds(nextToGateway);

        return apiClient.fetchMvelDetails(service.getServiceId())

                .flatMapMany(Flux::fromIterable)
                .filter(m -> "GW".equalsIgnoreCase(m.getValue()))
                .filter(m -> m.getNodeId().equals(gatewayId))

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
                                        null,
                                        nextNodeIds   
                                )
                                .flatMap(response -> {

                                    if (!response.isSuccess()) {
                                        return Mono.error(new SPRuntimeError(
                                                "Gateway MVEL failed",
                                                HttpStatus.BAD_GATEWAY,
                                                txn.getTxnId()
                                        ));
                                    }
                                    List<String> filteredNodeIds = response.getNextNodeList();

                                    if (filteredNodeIds == null || filteredNodeIds.isEmpty()) {
                                        return Mono.error(new SPRuntimeError(
                                                "No valid next node from gateway",
                                                HttpStatus.BAD_REQUEST,
                                                txn.getTxnId()
                                        ));
                                    }

                                    List<ServiceWorkFlow.Data.MappedTask> filteredTasks =
                                            nextToGateway.stream()
                                                    .filter(task ->
                                                            filteredNodeIds.contains(task.getNode().getId())
                                                    )
                                                    .toList();

                                    return Mono.just(filteredTasks);
                                })
                ).next().switchIfEmpty(Mono.just(nextToGateway));
    }
    private List<String> extractNextNodeIds(List<ServiceWorkFlow.Data.MappedTask> mappedTasks) {

        return mappedTasks.stream()
                .map(m -> m.getNode().getId())
                .filter(Objects::nonNull)
                .toList();
    }
    private TaskAvailableOfficeLocation nextAllowedOfficeLocation(List<ServiceWorkFlow.Data> wf, ServiceWorkFlow.Data.Nodes next,String txnId){
        TaskAvailableOfficeLocation location = new TaskAvailableOfficeLocation();
        location.setTaskId(next.getId());
        Optional<List<ServiceMeta.AvailableApplyLocations>> list = wf.stream()
                .filter(d -> d.getNode().getId().equals(next.getId()))
                .map(ServiceWorkFlow.Data::getAllowedOffices).findFirst();


        applicationFlowLogs.info("nextAllowedOfficeLocation for txnId {}  taskId {} is {}",txnId,next.getId(),list.orElseGet(List::of));

        location.setAllowedOffices(list.orElseGet(List::of));
        return location;
    }

}
