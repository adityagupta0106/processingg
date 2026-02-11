package com.serviceplus.form.validation.service;

import com.google.gson.reflect.TypeToken;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.kafka.KafkaProducer;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.serviceplus.form.validation.utility.ApplicationConstants.*;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.Utility.entityToString;

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

    public Mono<?> generateNextWorkflow(ApplicationDetails ad, ProcessingTxn savedLog, Services service, UserSessionObject user, CurrentProcess cp) {
                                        final String REDIS_KEY =SERVICE_WORKFLOW_REDIS_KEY_APPENDER.concat("_").concat(service.getServiceId().toString());

                                        Mono<Object> redisData = redis.fetch(
                                                REDIS_KEY, new TypeToken<ServiceWorkFlow>(){}.getType()
                                        );

                return redisData
                                .switchIfEmpty(
                                        apiClient.fetchProcessFlow(service.getBaseServiceId(), user,
                                                        savedLog.getApplicationId(), service.getTaskId(), service.getServiceId(),savedLog.getTxnId())
                                                .switchIfEmpty(Mono.error(new SPRuntimeError("Workflow Exception [ERR - 01]", HttpStatus.FAILED_DEPENDENCY)))
                                                .flatMap(Mono::just)
                )
                .flatMap(response -> generate((ServiceWorkFlow) response, ad, savedLog, service, user,cp))
                .onErrorResume(ex -> {
                    ex.printStackTrace();
                    return Mono.error(new SPRuntimeError("Workflow Error [ERR -01]",HttpStatus.INTERNAL_SERVER_ERROR));
                });
    }

    private Mono<?> generate(ServiceWorkFlow response, ApplicationDetails ad, ProcessingTxn savedLog,
                             Services service, UserSessionObject user, CurrentProcess cp) {
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

    private Mono<?> calculateNextWorkflow(ServiceWorkFlow.Data.Nodes node, ServiceWorkFlow.Data data
                                                , Services service, ApplicationDetails ad, ProcessingTxn txn
                                                , UserSessionObject user, List<ServiceWorkFlow.Data> wf, CurrentProcess currentActionProcess) {

        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        List<ServiceWorkFlow.Data.MappedTask> mappedTasks = data.getMappedTasks();
        List<CurrentProcess> nextCurrentProcess = new ArrayList<>();
        List<TaskAvailableOfficeLocation> taskAvailableOfficeLocations = new ArrayList<>();
        AtomicInteger i = new AtomicInteger(0);

        mappedTasks.forEach(task -> {
            ServiceWorkFlow.Data.Nodes next = task.getNode();
            // else if(TYPE_TASK.equals(next.getType())){

            CurrentProcess currentProcessCurrent = new CurrentProcess();
            currentProcessCurrent.setPreviousProcessId(currentActionProcess.getProcessId());
            currentProcessCurrent.setCurrentTask(next.getId());
            currentProcessCurrent.setCurrentTaskName(next.getName());
            currentProcessCurrent.setPreviousTask(node.getId());
            currentProcessCurrent.setPreviousTaskName(node.getName());
            currentProcessCurrent.setServiceId(service.getServiceId());
            currentProcessCurrent.setActionCode(FALLBACK_ACTION_NO);
            currentProcessCurrent.setApplicationId(ad.getApplicationId());
            currentProcessCurrent.setTenantId(user.getTenantId());
            currentProcessCurrent.setBaseServiceId(service.getBaseServiceId());
            currentProcessCurrent.setFormId(next.getFormId());
            currentProcessCurrent.setInitiatedOn(now);


            if(TYPE_GATEWAY.equals(next.getType())){
                currentProcessCurrent.setActionTaken("Y");
                currentProcessCurrent.setProcessId(createUniqueId());
                currentProcessCurrent.setActionOn(now);
            }
            else{
                currentProcessCurrent.setInitiatedOn(now);
                currentProcessCurrent.setProcessId(createUniqueId());
                taskAvailableOfficeLocations.add(nextAllowedOfficeLocation(wf,next,txn.getTxnId()));
//                currentProcessCurrent.setProcessId(createUniqueId().concat("_temp"));
            }

            currentProcessCurrent.setNewEntity(true);
            nextCurrentProcess.add(currentProcessCurrent);

            //}

            applicationFlowLogs.info("Creating current process for txnId {}  task {}",txn.getTxnId(),next);

            if(TYPE_GATEWAY.equals(next.getType())){

                String behaviour = next.getBehaviour();
                ServiceWorkFlow.Data nextToGatewayData = fetchNode(wf, next.getId());

                if (behaviour.equals(GATEWAY_BEHAVIOUR_EXCLUSIVE_DIVERGENT)
                        || behaviour.equals(GATEWAY_BEHAVIOUR_INCLUSIVE_DIVERGENT) || behaviour.equals(GATEWAY_BEHAVIOUR_PARALLEL_DIVERGENT)) {

                    //NEED TO CALL DATA STORE TO FETCH TASK AND USER ATTRIBUTE VALUES

                    List<ServiceWorkFlow.Data.MappedTask> nextToGateway = nextToGatewayData.getMappedTasks();
                    nextToGateway.forEach(nextToGatewayTask -> {

                        ServiceWorkFlow.Data.Nodes nodes = nextToGatewayTask.getNode();

                        applicationFlowLogs.info("Creating current process next to gateway for txnId {}  task {}",txn.getTxnId(),nodes);

                        CurrentProcess currentProcess = new CurrentProcess();
                        currentProcess.setPreviousProcessId(currentProcessCurrent.getProcessId());
                        currentProcess.setCurrentTask(nodes.getId());
                        currentProcess.setCurrentTaskName(nodes.getName());
                        currentProcess.setPreviousTask(next.getId());
                        currentProcess.setPreviousTaskName(next.getName());
                        currentProcess.setServiceId(service.getServiceId());
                        currentProcess.setActionCode(FALLBACK_ACTION_NO);
                        currentProcess.setApplicationId(ad.getApplicationId());
                        currentProcess.setTenantId(user.getTenantId());
                        currentProcess.setActionTaken("N");
                        currentProcess.setInitiatedOn(now);
                        currentProcess.setNewEntity(true);
                        currentProcess.setProcessId(createUniqueId());
                        currentProcess.setBaseServiceId(service.getBaseServiceId());
                        currentProcess.setFormId(nodes.getFormId());

                        if(i.get() == 1){
                           // currentProcess.setProcessId(null);
                        }
                        i.getAndIncrement();

                        nextCurrentProcess.add(currentProcess);
                        taskAvailableOfficeLocations.add(nextAllowedOfficeLocation(wf,nodes,txn.getTxnId()));

                    });

                }
                else if(behaviour.equals(GATEWAY_BEHAVIOUR_EXCLUSIVE_CONVERGENT)
                        || behaviour.equals(GATEWAY_BEHAVIOUR_INCLUSIVE_CONVERGENT) || behaviour.equals(GATEWAY_BEHAVIOUR_PARALLEL_CONVERGENT)){

                    ServiceWorkFlow.Data.Nodes nodes = nextToGatewayData.getNode();
                    CurrentProcess currentProcess = new CurrentProcess();
                    currentProcess.setPreviousProcessId(currentProcessCurrent.getProcessId());
                    currentProcess.setCurrentTask(nodes.getId());
                    currentProcess.setCurrentTaskName(nodes.getName());
                    currentProcess.setPreviousTask(next.getId());
                    currentProcess.setPreviousTaskName(next.getName());
                    currentProcess.setServiceId(service.getServiceId());
                    currentProcess.setActionOn(now);
                    currentProcess.setActionCode(FALLBACK_ACTION_NO);
                    currentProcess.setApplicationId(ad.getApplicationId());
                    currentProcess.setTenantId(user.getTenantId());
                    currentProcess.setActionTaken("N");

                    currentProcess.setNewEntity(true);
                   // currentProcess.setProcessId(createUniqueId().concat("_temp"));
                    currentProcess.setProcessId(createUniqueId());
                    currentProcess.setBaseServiceId(service.getBaseServiceId());
                    currentProcess.setFormId(nodes.getFormId());

                    nextCurrentProcess.add(currentProcess);
                    taskAvailableOfficeLocations.add(nextAllowedOfficeLocation(wf,nodes,txn.getTxnId()));
                }

            }
        });

        nextCurrentProcess.add(currentActionProcess);
        InboxKafka inboxKafkaDto = new InboxKafka();
        inboxKafkaDto.setLocationId(service.getSelectedLocationByUser());
        inboxKafkaDto.setLocationName(service.getSelectedLocationNameByUser());
        inboxKafkaDto.setProcessList(nextCurrentProcess);
        inboxKafkaDto.setOfficeDetails(taskAvailableOfficeLocations);
        inboxKafkaDto.setServiceName(service.getServiceName());

        return Mono.just(inboxKafkaDto);
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

    private TaskAvailableOfficeLocation nextAllowedOfficeLocation(List<ServiceWorkFlow.Data> wf, ServiceWorkFlow.Data.Nodes next,String txnId){
        TaskAvailableOfficeLocation location = new TaskAvailableOfficeLocation();
        location.setTaskId(next.getId());
        Optional<List<Services.AvailableApplyLocations>> list = wf.stream()
                .filter(d -> d.getNode().getId().equals(next.getId()))
                .map(ServiceWorkFlow.Data::getAllowedOffices).findFirst();


        applicationFlowLogs.info("nextAllowedOfficeLocation for txnId {}  taskId {} is {}",txnId,next.getId(),list.orElseGet(List::of));

        location.setAllowedOffices(list.orElseGet(List::of));
        return location;
    }

}
