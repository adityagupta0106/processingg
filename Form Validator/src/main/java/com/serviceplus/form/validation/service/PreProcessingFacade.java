package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.HandlerResponse;
import com.serviceplus.form.validation.entity.*;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;

import static com.serviceplus.form.validation.utility.Utility.*;
import static java.util.Objects.isNull;

import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Service
public class PreProcessingFacade {

    @Autowired
    private ReactiveApiClient reactiveApiClient;

    @Autowired
    private ProcessingTxnRepository txnRepository;

    @Autowired
    private TempTransactionLogService tempTransactionLogService;

    @Autowired
    private CurrentProcessRepository currentProcessRepository;

    @Autowired
    private RedisService redis;

    @Autowired
    private TransactionalDBExecutor transactionalDBExecutor;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    public Mono<List<ServiceMeta>> getServiceList(UserSessionObject user) {
        return reactiveApiClient.fetchServiceList(user)
            .map(services -> {
                services.forEach(service -> {
                	service.setServiceKey(encryptServiceKeys(service));
                });
                return services;
            });
    }

    public Mono<HandlerResponse> getFormDataAndSaveTempTxn(ServiceMeta service, UserSessionObject user, ServerHttpRequest request) {

        return tempTransactionLogService.mergeTransactionLog(service,user,request)
                    .flatMap(data -> {
                        if(isNull(data)) {
                            return Mono.empty();
                         }
                        else {
                          return  reactiveApiClient.fetchFormData(data.getTxnId(), service,user);
                        }
                    });

    }

    public Mono<ProcessingTxn> getFormDataAndSaveTxn(ServiceMeta service, UserSessionObject user, ServerHttpRequest request,
                                                     TempTransactionLogs tempLog, String appId, String dataId, String activityType, boolean newEntityFlag) {
        String txnId=tempLog.getTxnId();
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        String applicationId = isEmpty(appId) ?  createUniqueId() : appId;

        applicationFlowLogs.info("Saving txn log for txnId {} applicationId {} dataId {} ",txnId,applicationId,dataId);

        ProcessingTxn txnEntity = new ProcessingTxn(
            txnId,
            service.getFormId(),
            service.getServiceId(),
            service.getTaskId(),
            dt,
            null,
            user.getUserID(),
            user.getTenantId(),
            getClientIpAddr(request),
                applicationId
        );

        if(tempLog!= null && tempLog.getStartTime() != null) {
            txnEntity.setStartTime(dt);
        }

        txnEntity.setNewEntity(newEntityFlag);

        ApplicationDetails applicationDetails = new ApplicationDetails(
                applicationId,
                "DRAFT_".concat(txnId),
                service.getServiceId(),
                now,
                user.getUserName(),
                "S",
                user.getUserID().longValue(),
                user.getTenantId()
        );

        applicationDetails.setNewEntity(newEntityFlag);
        applicationDetails.setAppliedLocationId(service.getLocations().getFirst().getLocationId().intValue());
        applicationDetails.setAppliedLocationName(service.getLocations().getFirst().getLocationName());

        ApplicationFlowStatusEntity flowStatus = new ApplicationFlowStatusEntity();
        flowStatus.setId(createUniqueId());
        flowStatus.setApplicationId(applicationId);
        flowStatus.setFormId(service.getFormId());
        flowStatus.setTxnId(txnId);
        flowStatus.setActivityType("FS");
        flowStatus.setCompleted(0);
        flowStatus.setTenantId(user.getTenantId());
        flowStatus.setNewEntity(true);
        flowStatus.setTaskId(service.getTaskId());
        flowStatus.setServiceId(service.getServiceId());
        flowStatus.setLastUpdate(now);

        return transactionalDBExecutor.execute(txnId,applicationDetails, flowStatus, txnEntity)
                .then(redis.remove(txnId))
                .thenReturn(txnEntity)
                .onErrorResume(Exception.class, ex ->
                        Mono.error(new SPRuntimeError(
                                "Unable to process your request [AY - 01]",
                                HttpStatus.INTERNAL_SERVER_ERROR,txnId
                        ))
                );


//        Mono<ProcessingTxn> res = applicationDetailsRepository.save(applicationDetails)
//                .then(currentProcessRepository.save(currentProcess))
//                .then(txnRepository.save(txnEntity));



//        return transactionalOperator.execute(status ->
//                applicationDetailsRepository.save(applicationDetails)
//                        .then(currentProcessRepository.save(currentProcess))
//                        .then(txnRepository.save(txnEntity))
//        ).single();

    }

    public Mono<ProcessingTxn> saveTransactionReactive(ProcessingTxn txnEntity) {
        return txnRepository.save(txnEntity);
    }


    public ServiceMeta decryptApplyKey(String applyKey) {
        return decryptServiceKeys(applyKey);
    }

    public Mono<ServerResponse> fetchServiceKey(Integer baseServiceId, UserSessionObject user, ServerHttpRequest request, String appId, String taskId, Integer serviceId) {
        return reactiveApiClient.fetchServiceKey(baseServiceId,user,appId,taskId,serviceId)
                .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }
}




