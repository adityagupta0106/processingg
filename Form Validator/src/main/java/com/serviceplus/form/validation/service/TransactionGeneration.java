package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.dto.Services;
import com.serviceplus.form.validation.dto.TaskActivity;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.Utility.getClientIpAddr;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

@Service
@SanitizeRequest
public class TransactionGeneration {

    @Autowired
    private TransactionalDBExecutor transactionalDBExecutor;

    public Mono<ProcessingTxn> createNewTransactionAndFlow(Services service, String appId, TaskActivity.ActivityData nextActivity, UserSessionObject user,
                                             ServerHttpRequest request){

        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());

        ProcessingTxn txnLog = new ProcessingTxn();
        txnLog.setTxnId(createUniqueId());
        txnLog.setFormId(service.getFormId());
        txnLog.setServiceId(service.getServiceId());
        txnLog.setTaskId(service.getTaskId());
        txnLog.setApplicationId(appId);
        txnLog.setUserId(user.getUserID());
        txnLog.setUserIp(getClientIpAddr(request));
        txnLog.setStartTime(now);
        txnLog.setTenantId(user.getTenantId());
        txnLog.setActivityType(nextActivity.getActivityType());

        ApplicationFlowStatusEntity flowStatusEntity = new ApplicationFlowStatusEntity();
        flowStatusEntity.setId(createUniqueId());
        flowStatusEntity.setApplicationId(appId);
        flowStatusEntity.setFormId(service.getFormId());
        flowStatusEntity.setTxnId(txnLog.getTxnId());
        flowStatusEntity.setActivityType(nextActivity.getActivityType());
        flowStatusEntity.setCompleted(0);
        flowStatusEntity.setTenantId(user.getTenantId());
        flowStatusEntity.setNewEntity(true);
        flowStatusEntity.setTaskId(service.getTaskId());
        flowStatusEntity.setServiceId(service.getServiceId());
        flowStatusEntity.setLastUpdate(now);

        return merge(txnLog,flowStatusEntity);

    }

    public Mono<ProcessingTxn> createNewTransactionAndUpdateInFlow(Services service, ApplicationFlowStatusEntity flowStatusEntity,ServerHttpRequest request){
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        UserSessionObject user = getUserSessionDetails(request);

        ProcessingTxn txnLog = new ProcessingTxn();
        txnLog.setTxnId(createUniqueId());
        txnLog.setFormId(service.getFormId());
        txnLog.setServiceId(service.getServiceId());
        txnLog.setTaskId(service.getTaskId());
        txnLog.setApplicationId(flowStatusEntity.getApplicationId());
        txnLog.setUserId(user.getUserID());
        txnLog.setUserIp(getClientIpAddr(request));
        txnLog.setStartTime(now);
        txnLog.setTenantId(user.getTenantId());
        txnLog.setActivityType(flowStatusEntity.getActivityType());

        flowStatusEntity.setTxnId(txnLog.getTxnId());
        flowStatusEntity.setTenantId(user.getTenantId());

        return merge(txnLog,flowStatusEntity);
    }

    private Mono<ProcessingTxn> merge(ProcessingTxn txnLog,ApplicationFlowStatusEntity flowStatusEntity){
        return transactionalDBExecutor.execute(txnLog,flowStatusEntity).thenReturn(txnLog);
    }
}
