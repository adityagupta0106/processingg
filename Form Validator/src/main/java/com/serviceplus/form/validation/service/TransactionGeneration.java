package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ActivityMapDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
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
import java.util.Objects;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_STATUS_KEY;
import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.Utility.getClientIpAddr;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

@Service
@SanitizeRequest
public class TransactionGeneration {

    private final TransactionalDBExecutor transactionalDBExecutor;

    private final ApplicationFlowRouterRepository applicationFlowRepository;

    private final CurrentProcessRepository currentProcessRepository;

    public TransactionGeneration(TransactionalDBExecutor transactionalDBExecutor, ApplicationFlowRouterRepository applicationFlowRepository, CurrentProcessRepository currentProcessRepository) {
        this.transactionalDBExecutor = transactionalDBExecutor;
        this.applicationFlowRepository = applicationFlowRepository;
        this.currentProcessRepository = currentProcessRepository;
    }

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
        flowStatusEntity.setActivityConfigId(nextActivity.getActivityConfigId());
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

    public Mono<ProcessingTxn> editApplication(ServiceMeta service, String appId, String txnId, ServerHttpRequest request) {

        UserSessionObject user = Objects.requireNonNull(getUserSessionDetails(request));

        Mono<ApplicationFlowStatusEntity> flow =
                applicationFlowRepository
                        .findFirstByApplicationIdAndCompletedAndTaskIdAndServiceIdAndTenantIdAndActivityTypeOrderByIdDesc(
                                appId,
                                1,
                                service.getTaskId(),
                                service.getServiceId(),
                                user.getTenantId(),
                                ACTIVITY_FORM_STATUS_KEY
                        );

        return flow
                .switchIfEmpty(
                        Mono.error(
                                new SPRuntimeError(
                                        "Unable to process the request check parameter",
                                        HttpStatus.BAD_REQUEST,
                                        txnId
                                )
                        )
                )
                .flatMap(result ->

                        currentProcessRepository
                                .findByApplicationIdAndCurrentTaskAndActionTaken(
                                        appId,
                                        service.getTaskId(),
                                        "N"
                                )
                                .switchIfEmpty(
                                        Mono.error(
                                                new SPRuntimeError(
                                                        "Current process not found",
                                                        HttpStatus.BAD_REQUEST,
                                                        txnId
                                                )
                                        )
                                )
                                .flatMap(currentProcess -> {

                                    String processId = currentProcess.getProcessId();

                                    applicationFlowLogs.info("EDIT | TxnId: {} | ApplicationId: {} | ProcessId: {}", txnId, appId, processId);

                                    /*
                                     * 1. Change status to system execution code
                                     */
                                    String flowUpdate = """
                                        UPDATE %s
                                        SET completed = 2
                                        WHERE task_id = :taskId
                                          AND service_id = :serviceId
                                          AND application_id = :applicationId
                                          AND txn_id = :txnId
                                          AND completed = 0
                                          AND tenant_id = :tenantId
                                        """;

                                    String flowSql = String.format(
                                            flowUpdate,
                                            SP_SCHEMA_NAME.concat(".application_flow_status")
                                    );

                                    return transactionalDBExecutor.executeRawSql(
                                            flowSql,
                                            Map.of(
                                                    "taskId", service.getTaskId(),
                                                    "serviceId", service.getServiceId(),
                                                    "applicationId", appId,
                                                    "txnId", txnId,
                                                    "tenantId", user.getTenantId()
                                            ),
                                            txnId
                                    ).flatMap(rows -> {

                                        applicationFlowLogs.info("EDIT | Flow updated | ApplicationId: {} | TxnId: {} | Rows: {}", appId, txnId, rows);

                                        if (rows == 0) {
                                            return Mono.error(new SPRuntimeError("Unable to process request [EDIT - 01]", HttpStatus.INTERNAL_SERVER_ERROR, txnId));
                                        }

                                        /*
                                         * 2. Document Log -> R
                                         */
                                        String documentLogUpdate = """
                                            UPDATE %s
                                            SET status = 'R'
                                            WHERE application_id = :applicationId
                                              AND process_id = :processId
                                              AND tenant_id = :tenantId
                                              AND status = 'P'
                                            """;

                                        String documentLogSql = String.format(
                                                documentLogUpdate,
                                                SP_SCHEMA_NAME.concat(".application_document_log")
                                        );

                                        return transactionalDBExecutor.executeRawSql(
                                                documentLogSql,
                                                Map.of(
                                                        "applicationId", appId,
                                                        "processId", processId,
                                                        "tenantId", user.getTenantId()
                                                ),
                                                txnId
                                        );
                                    }).flatMap(documentRows -> {

                                        applicationFlowLogs.info("EDIT | Document logs marked R | ApplicationId: {} | ProcessId: {} | Rows: {}", appId, processId, documentRows);

                                        /*
                                         * 3. Document Merge -> R
                                         */
                                        String mergeUpdate = """
                                            UPDATE %s
                                            SET status = 'R',
                                                updated_by = :userId,
                                                updated_on = CURRENT_TIMESTAMP
                                            WHERE application_id = :applicationId
                                              AND process_id = :processId
                                              AND tenant_id = :tenantId
                                              AND status = 'P'
                                            """;

                                        String mergeSql = String.format(
                                                mergeUpdate,
                                                SP_SCHEMA_NAME.concat(".application_document_merge")
                                        );

                                        return transactionalDBExecutor.executeRawSql(
                                                mergeSql,
                                                Map.of(
                                                        "applicationId", appId,
                                                        "processId", processId,
                                                        "tenantId", user.getTenantId(),
                                                        "userId", user.getUserID()
                                                ),
                                                txnId
                                        );
                                    }).flatMap(mergeRows -> {

                                        applicationFlowLogs.info("EDIT | Merge records marked R | ApplicationId: {} | ProcessId: {} | Rows: {}", appId, processId, mergeRows);

                                        /*
                                         * 4. Document Submission -> R
                                         */
                                        String submissionUpdate = """
                                            UPDATE %s
                                            SET status = 'R'
                                            WHERE application_id = :applicationId
                                              AND process_id = :processId
                                              AND tenant_id = :tenantId
                                              AND status = 'P'
                                            """;

                                        String submissionSql = String.format(
                                                submissionUpdate,
                                                SP_SCHEMA_NAME.concat(".application_document_submission")
                                        );

                                        return transactionalDBExecutor.executeRawSql(
                                                submissionSql,
                                                Map.of(
                                                        "applicationId", appId,
                                                        "processId", processId,
                                                        "tenantId", user.getTenantId()
                                                ),
                                                txnId
                                        );
                                    }).flatMap(submissionRows -> {

                                        applicationFlowLogs.info(
                                                "EDIT | Submission records marked R | ApplicationId: {} | ProcessId: {} | Rows: {}",
                                                appId,
                                                processId,
                                                submissionRows
                                        );

                                        return createNewTransactionAndFlow(
                                                service,
                                                appId,
                                                new ActivityMapDTO.ActivityData(
                                                        ACTIVITY_FORM_STATUS_KEY
                                                ),
                                                user,
                                                request,
                                                result.getDataId()
                                        );
                                    });
                                })
                );
    }


}
