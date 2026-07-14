package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.OFFICIAL_TASK_FLAG;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.*;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

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
                                                     TempTransactionLogs tempLog, String appId, String dataId, String activityType, boolean newEntityFlag,
                                                     ApplicationFlowStatusEntity oldFlowStatus) {
        String txnId=tempLog.getTxnId();
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        String applicationId = isEmpty(appId) ?  createUniqueId() : appId;

        applicationFlowLogs.info(
                "Saving transaction started for txnId : {} applicationId : {} dataId : {}",
                txnId,
                applicationId,
                dataId);

        applicationFlowLogs.info(
                "Preparing ProcessingTxn entity for txnId : {}",
                txnId);

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

        if(tempLog.getStartTime() != null) {
            applicationFlowLogs.info(
                    "Updating start time for existing txnId : {}",
                    txnId);
            txnEntity.setStartTime(dt);
        }

        txnEntity.setNewEntity(newEntityFlag);

        applicationFlowLogs.info(
                "Preparing ApplicationDetails entity for applicationId : {}",
                applicationId);

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
        applicationDetails.setAppliedLocationId(service.getLocations().getFirst().getOrgUnitCode().intValue());
        applicationDetails.setAppliedLocationName(service.getLocations().getFirst().getOrgUnitName());
        applicationDetails.setServiceName(service.getServiceName());

        applicationFlowLogs.info(
                "Preparing ApplicationFlowStatusEntity for txnId : {}",
                txnId);

        ApplicationFlowStatusEntity flowStatus = new ApplicationFlowStatusEntity();

        if(isNull(oldFlowStatus) || isNull(oldFlowStatus.getId())){
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
        }
        else{
            flowStatus = oldFlowStatus;
        }


        applicationFlowLogs.info(
                "Executing transactional DB operation for txnId : {} newEntityFlag : {}",
                txnId,
                newEntityFlag);

        if(newEntityFlag) {

            return transactionalDBExecutor
                    .execute(
                            txnId,
                            applicationDetails,
                            flowStatus,
                            txnEntity)
                    .doOnSuccess(success ->
                            applicationFlowLogs.info(
                                    "Transaction saved successfully for txnId : {}",
                                    txnId))
                    .then(redis.remove(txnId))
                    .doOnSuccess(success ->
                            applicationFlowLogs.info(
                                    "Redis temporary transaction removed successfully for txnId : {}",
                                    txnId))
                    .thenReturn(txnEntity)
                    .onErrorResume(Exception.class, ex -> {

                        applicationFlowLogs.error(
                                "Unable to process transaction for txnId : {} error : {}",
                                txnId,
                                ex.getMessage(),
                                ex);

                        return Mono.error(
                                new SPRuntimeError(
                                        "Unable to process your request [AY - 01]",
                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                        txnId
                                ));
                    });
        }

        return transactionalDBExecutor
                .execute(
                        txnId,
                        txnEntity,
                        flowStatus)
                .doOnSuccess(success ->
                        applicationFlowLogs.info(
                                "Transaction updated successfully for txnId : {}",
                                txnId))
                .then(redis.remove(txnId))
                .doOnSuccess(success ->
                        applicationFlowLogs.info(
                                "Redis temporary transaction removed successfully for txnIda : {}",
                                txnId))
                .thenReturn(txnEntity)
                .onErrorResume(Exception.class, ex -> {

                    applicationFlowLogs.error(
                            "Unable to update transaction for txnId : {} error : {}",
                            txnId,
                            ex.getMessage(),
                            ex);

                    return Mono.error(
                            new SPRuntimeError(
                                    "Unable to process your request [AY - 02]",
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    txnId
                            ));
                });


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

    public Mono<ServerSidePaginationRecord<WorkflowInboxResponse>> getWFPInbox(ServerHttpRequest request, UserSessionObject user) {

        return reactiveApiClient.fetchWFPInbox(request,user).map(inboxList -> {
            DateTimeFormatter formatter =  DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a");
            List<WorkflowInboxResponse> data = inboxList.getData();
            data.forEach(inbox -> {

                ServiceMeta service = getServiceMeta(inbox);
                inbox.setTaskType(OFFICIAL_TASK_FLAG);
                inbox.setServiceKey(encryptServiceKeys(service));
                if(inbox.getApplRecievedOn() != null) {

                    String formattedDate =
                            inbox.getApplRecievedOn()
                                    .toInstant()
                                    .atZone(
                                            ZoneId.systemDefault())
                                    .format(formatter);

                    inbox.setReceivedDate(formattedDate);
                }
            });

            return inboxList;
        });
    }

    private static ServiceMeta getServiceMeta(WorkflowInboxResponse inbox) {
        ServiceMeta service = new ServiceMeta();

        service.setServiceId(inbox.getServiceId());
        service.setServiceName(inbox.getServiceName());
        service.setFormId(inbox.getFormId());
        service.setTaskId(inbox.getTaskId());
        service.setTaskType(OFFICIAL_TASK_FLAG);
        service.setBaseServiceId(inbox.getBaseServiceId());
        service.setCurrentProcessId(inbox.getCurrentProcessId());

        ServiceMeta.AvailableApplyLocations location = new ServiceMeta.AvailableApplyLocations();
        location.setOrgUnitCode(inbox.getLocationId().longValue());
        location.setLocationName("");

        service.setLocations(List.of(location));
        return service;
    }

	public Mono<ServerSidePaginationRecord<WorkflowInboxResponse>> getInboxApplications(ServerHttpRequest request, UserSessionObject user) {

        return reactiveApiClient.getInboxApplications(request,user).map(inboxList -> {
            DateTimeFormatter formatter =  DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a");
            List<WorkflowInboxResponse> data = inboxList.getData();
            data.forEach(inbox -> {

                ServiceMeta service = getServiceMeta(inbox);
                inbox.setTaskType(OFFICIAL_TASK_FLAG);
                inbox.setServiceKey(encryptServiceKeys(service));
                if(inbox.getApplRecievedOn() != null) {

                    String formattedDate =
                            inbox.getApplRecievedOn()
                                    .toInstant()
                                    .atZone(
                                            ZoneId.systemDefault())
                                    .format(formatter);

                    inbox.setReceivedDate(formattedDate);
                }
            });

            return inboxList;
        });
    }
}




