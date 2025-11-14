package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ServiceWorkFlow;
import com.serviceplus.form.validation.dto.Services;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static com.serviceplus.form.validation.utility.ApplicationConstants.*;

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

    public Mono<?> generateNextWorkflow(ApplicationDetails ad, ProcessingTxn savedLog, Services service, UserSessionObject user) {
        final String REDIS_KEY =SERVICE_WORKFLOW_REDIS_KEY_APPENDER.concat("_").concat(service.getServiceId().toString());

        Mono<Object> redisData = redis.fetch(
                REDIS_KEY, ServiceWorkFlow.class
        );

        return redisData
                .switchIfEmpty(
                        apiClient.fetchProcessFlow(service.getBaseServiceId(), user,
                                        savedLog.getApplicationId(), service.getTaskId(), service.getServiceId(),savedLog.getTxnId())
                                .switchIfEmpty(Mono.error(new SPRuntimeError("Workflow Exception [ERR - 01]", HttpStatus.FAILED_DEPENDENCY)))
                                .flatMap(response -> {
                                        redis.add(response,REDIS_KEY,false).subscribe();
                                        return generate((ServiceWorkFlow) response, ad, savedLog, service, user);
                                })
                )
                .flatMap(response -> generate((ServiceWorkFlow) response, ad, savedLog, service, user))
                .onErrorResume(ex -> {
                    ex.printStackTrace();
                    return Mono.error(new SPRuntimeError("Workflow Error [ERR -01]",HttpStatus.INTERNAL_SERVER_ERROR));
                });
    }

    private Mono<?> generate(ServiceWorkFlow response, ApplicationDetails ad, ProcessingTxn savedLog,
                             Services service, UserSessionObject user) {
        List<ServiceWorkFlow.Data> wf =  response.getData();
        String currentTask = service.getTaskId();

        applicationFlowLogs.info("Generating workflow for txnId {} currentTask {}",savedLog.getTxnId(),currentTask);

        ServiceWorkFlow.Data data = fetchNode(wf, currentTask);

        if(data != null) {
            applicationFlowLogs.info("Generating workflow for txnId {} currentTask {} nextNode {}"
                    ,savedLog.getTxnId(),currentTask,data.toString());

            calculateNextWorkflow(data.getNodes(), data, service, ad, savedLog, user, wf);
        }

        return Mono.empty();
    }

    private void calculateNextWorkflow(ServiceWorkFlow.Data.Nodes node, ServiceWorkFlow.Data data
                                                , Services service, ApplicationDetails ad, ProcessingTxn txn
                                                , UserSessionObject user, List<ServiceWorkFlow.Data> wf) {

        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        List<ServiceWorkFlow.Data.MappedTask> mappedTasks = data.getMappedTasks();
        List<CurrentProcess> nextCurrentProcess = new ArrayList<>();

        mappedTasks.forEach(task -> {
            ServiceWorkFlow.Data.Nodes next = task.getNodes();

            applicationFlowLogs.info("Creating current process for txnId {}  task {}",txn.getTxnId(),next);

            if(TYPE_GATEWAY.equals(next.getType())){

                String behaviour = next.getBehaviour();
                ServiceWorkFlow.Data nextToGatewayData = fetchNode(wf, next.getId());

                if (behaviour.equals(GATEWAY_BEHAVIOUR_EXCLUSIVE_DIVERGENT)
                        || behaviour.equals(GATEWAY_BEHAVIOUR_INCLUSIVE_DIVERGENT) || behaviour.equals(GATEWAY_BEHAVIOUR_PARALLEL_DIVERGENT)) {

                       //NEED TO CALL DATA STORE TO FETCH TASK AND USER ATTRIBUTE VALUES

                    List<ServiceWorkFlow.Data.MappedTask> nextToGateway = nextToGatewayData.getMappedTasks();
                    nextToGateway.forEach(nextToGatewayTask -> {

                        ServiceWorkFlow.Data.Nodes nodes = nextToGatewayTask.getNodes();

                        applicationFlowLogs.info("Creating current process next to gateway for txnId {}  task {}",txn.getTxnId(),next);

                        CurrentProcess currentProcess = new CurrentProcess();
                        currentProcess.setPreviousProcessId(txn.getTxnId());
                        currentProcess.setCurrentTask(nodes.getId());
                        currentProcess.setCurrentTaskName(nodes.getName());
                        currentProcess.setPreviousTask(node.getId());
                        currentProcess.setPreviousTaskName(node.getName());
                        currentProcess.setServiceId(service.getServiceId());
                        currentProcess.setActionOn(now);
                        currentProcess.setActionCode(FALLBACK_ACTION_NO);
                        currentProcess.setApplicationId(ad.getApplicationId());
                        currentProcess.setTenantId(user.getTenantId());

                        currentProcess.setNewEntity(true);

                        nextCurrentProcess.add(currentProcess);

                    });

                }
                else if(behaviour.equals(GATEWAY_BEHAVIOUR_EXCLUSIVE_CONVERGENT)
                        || behaviour.equals(GATEWAY_BEHAVIOUR_INCLUSIVE_CONVERGENT) || behaviour.equals(GATEWAY_BEHAVIOUR_PARALLEL_CONVERGENT)){

                        ServiceWorkFlow.Data.Nodes nodes = nextToGatewayData.getNodes();
                        CurrentProcess currentProcess = new CurrentProcess();
                        currentProcess.setPreviousProcessId(txn.getTxnId());
                        currentProcess.setCurrentTask(nodes.getId());
                        currentProcess.setCurrentTaskName(nodes.getName());
                        currentProcess.setPreviousTask(node.getId());
                        currentProcess.setPreviousTaskName(node.getName());
                        currentProcess.setServiceId(service.getServiceId());
                        currentProcess.setActionOn(now);
                        currentProcess.setActionCode(FALLBACK_ACTION_NO);
                        currentProcess.setApplicationId(ad.getApplicationId());
                        currentProcess.setTenantId(user.getTenantId());

                        currentProcess.setNewEntity(true);

                        nextCurrentProcess.add(currentProcess);
                }

            }
            else if(TYPE_TASK.equals(next.getType())){
                CurrentProcess currentProcess = new CurrentProcess();
                currentProcess.setPreviousProcessId(txn.getTxnId());
                currentProcess.setCurrentTask(next.getId());
                currentProcess.setCurrentTaskName(next.getName());
                currentProcess.setPreviousTask(node.getId());
                currentProcess.setPreviousTaskName(node.getName());
                currentProcess.setServiceId(service.getServiceId());
                currentProcess.setActionOn(now);
                currentProcess.setActionCode(FALLBACK_ACTION_NO);
                currentProcess.setApplicationId(ad.getApplicationId());
                currentProcess.setTenantId(user.getTenantId());

                currentProcess.setNewEntity(true);
                nextCurrentProcess.add(currentProcess);
            }
        });

        if(!nextCurrentProcess.isEmpty()) {
            currentProcessRepository.saveAll(nextCurrentProcess);
        }
    }

    private ServiceWorkFlow.Data fetchNode(List<ServiceWorkFlow.Data> wf , String task){
        for(ServiceWorkFlow.Data data : wf){
            ServiceWorkFlow.Data.Nodes nodes = data.getNodes();
            if(task.equals(nodes.getId())){
                return data;
            }
        }
        return null;
    }

}
