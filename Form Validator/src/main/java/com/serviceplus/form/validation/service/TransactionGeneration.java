package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ActivityMapDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_STATUS_KEY;
import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.Utility.getClientIpAddr;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

@Service
@SanitizeRequest
public class TransactionGeneration {

    @Autowired
    private TransactionalDBExecutor transactionalDBExecutor;

    @Autowired
    private ApplicationFlowRouterRepository applicationFlowRepository;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    public Mono<ProcessingTxn> createNewTransactionAndFlow(ServiceMeta service, String appId, ActivityMapDTO.ActivityData nextActivity, UserSessionObject user,
                                             ServerHttpRequest request,String dataId){

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
        flowStatusEntity.setDataId(dataId);

        flowStatusEntity.setUserSubmissionRequired(nextActivity.getUserSubmissionRequired());

        return merge(txnLog,flowStatusEntity);

    }

    public Mono<ProcessingTxn> createNewTransactionAndUpdateInFlow(ServiceMeta service, ApplicationFlowStatusEntity flowStatusEntity,ServerHttpRequest request){
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
        return transactionalDBExecutor.execute(txnLog.getTxnId(),txnLog,flowStatusEntity).thenReturn(txnLog);
    }

    public Mono<ProcessingTxn> editApplication(ServiceMeta service, String appId,
                                               String txnId, ServerHttpRequest request) {

        UserSessionObject user = getUserSessionDetails(request);

        Mono<ApplicationFlowStatusEntity> flow = applicationFlowRepository.findFirstByApplicationIdAndCompletedAndTaskIdAndServiceIdAndTenantIdAndActivityTypeOrderByIdDesc(
                appId,1, service.getTaskId(), service.getServiceId(), user.getTenantId(),ACTIVITY_FORM_STATUS_KEY
        );


        return flow
                .switchIfEmpty(
                        Mono.error(new SPRuntimeError("Unable to process the request check parameter",HttpStatus.BAD_REQUEST,txnId))
                )
                .flatMap(result -> {

            applicationFlowLogs.info("Updating the flow status to completed by system for application {} txnId {}",appId,txnId);

            String string = """
                 UPDATE %s SET completed = 2 WHERE task_id = :taskId AND service_id = :serviceId AND application_id = :applicationId AND txn_id = :txnId 
                 AND completed = 0 AND tenant_id = :tenantId;
                """;

            String sql = String.format(string, SP_SCHEMA_NAME.concat(".application_flow_status"));

            return transactionalDBExecutor.executeRawSql(sql, Map.of("taskId", service.getTaskId(),
                    "serviceId", service.getServiceId(), "applicationId", appId,"txnId",txnId,"tenantId",user.getTenantId()),txnId
            ).flatMap(rows -> {

                applicationFlowLogs.info("Updated the flow status to completed by system for application {} txnId {} rows {}",appId,txnId,rows);

                if(rows == 0){
                    return Mono.error(new SPRuntimeError("Unable to process request [EDIT - 01]", HttpStatus.INTERNAL_SERVER_ERROR,txnId));
                }
                return createNewTransactionAndFlow(
                        service, appId, new ActivityMapDTO.ActivityData(ACTIVITY_FORM_STATUS_KEY), user,request, result.getDataId()
                        );
            });
        });
    }
}
