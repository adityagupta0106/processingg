package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.FALLBACK_ACTION_NO;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.dto.Services;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;

import static com.serviceplus.form.validation.utility.Utility.*;
import static java.util.Objects.isNull;

import org.springframework.transaction.reactive.TransactionalOperator;
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
    private ApplicationDetailsRepository applicationDetailsRepository;

    @Autowired
    private CurrentProcessRepository currentProcessRepository;

    @Autowired
    private TransactionalOperator transactionalOperator;

    @Autowired
    private RedisService redis;

    public Mono<List<Services>> getServiceList(UserSessionObject user) {
        return reactiveApiClient.fetchServiceList(user)
            .map(services -> {
//                services.forEach(service -> {
//                	service.setServiceKey(encryptServiceKeys(service));
//                });
                return services;
            });
    }

    public Mono<Map<String,Object>> getFormDataAndSaveTempTxn(Services service, UserSessionObject user, ServerHttpRequest request) {

        return tempTransactionLogService.mergeTransactionLog(service,user,request)
                    .flatMap(data -> {
                        if(isNull(data)) {
                            return Mono.empty();
                         }
                        else {
                          return  reactiveApiClient.fetchFormData(data.getTxnId(), service.getFormId());
                        }
                    });

    }


    public Mono<ProcessingTxn> getFormDataAndSaveTxn(Services service, UserSessionObject user, ServerHttpRequest request,
                                                     TempTransactionLogs tempLog, String appId, String dataId, String fs) {
        String txnId;
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        String applicationId = isEmpty(appId) ?  createUniqueId() : appId;

        if(tempLog == null) {
            txnId = createUniqueId();
        }
        else{
            txnId = tempLog.getTxnId();
            if(tempLog.getStartTime() != null) {
                dt = tempLog.getStartTime().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            }
            service = tempLog.getService();
        }


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
            txnEntity.setFormStartTime(dt);
        }

        txnEntity.setNewEntity(isEmpty(appId));

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

        applicationDetails.setNewEntity(isEmpty(appId));

        CurrentProcess currentProcess = new CurrentProcess();
        currentProcess.setProcessId(txnId);
        currentProcess.setPreviousProcessId("");
        currentProcess.setCurrentTask(service.getTaskId());
        currentProcess.setPreviousTask("");
        currentProcess.setServiceId(service.getServiceId());
        currentProcess.setActionOn(now);
        currentProcess.setActionCode(FALLBACK_ACTION_NO);
        currentProcess.setApplicationId(applicationId);
        currentProcess.setCurrentTaskName("");
        currentProcess.setPreviousTaskName("");
        currentProcess.setTenantId(user.getTenantId());
        currentProcess.setDataId(dataId);

        currentProcess.setNewEntity(isEmpty(appId));
        redis.remove(txnId).subscribe();

        return  applicationDetailsRepository.save(applicationDetails)
                .then(currentProcessRepository.save(currentProcess))
                .then(txnRepository.save(txnEntity));




//        return transactionalOperator.execute(status ->
//                applicationDetailsRepository.save(applicationDetails)
//                        .then(currentProcessRepository.save(currentProcess))
//                        .then(txnRepository.save(txnEntity))
//        ).single();

    }

    public Mono<ProcessingTxn> saveTransactionReactive(ProcessingTxn txnEntity) {
        return txnRepository.save(txnEntity);
    }


    public Services decryptApplyKey(String applyKey) {
        return descryptServiceKeys(applyKey);
    }

    public Mono<ServerResponse> fetchServiceKey(Integer baseServiceId, UserSessionObject user, ServerHttpRequest request, String appId, String taskId, Integer serviceId) {
        return reactiveApiClient.fetchServiceKey(baseServiceId,user,appId,taskId,serviceId);
    }
}




