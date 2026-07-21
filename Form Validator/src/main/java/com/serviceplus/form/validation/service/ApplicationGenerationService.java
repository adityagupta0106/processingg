package com.serviceplus.form.validation.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.kafka.KafkaProducer;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;

import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.ApplicationConstants.*;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.Utility.*;

@Service
public class ApplicationGenerationService {
	
	@Autowired
    private ReactiveApiClient reactiveApiClient;
    
    @Autowired
    private KafkaProducer kafka;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ApplicationDetailsRepository applicationDetailsRepository;

    @Autowired
    private TransactionalOperator transactionalOperator;

    @Autowired
    private CurrentProcessRepository currentProcessRepository;

    @Autowired
    private TransactionalDBExecutor transactionalDBExecutor;

    @Autowired
    private KafkaProducer kafkaProducer;

    @Value("${push.form.submission.data.inbox.topic}")
    private String PUSH_FORM_SUBMISSION_DATA_INBOX_TOPIC;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");
    
    public Mono<ServerResponse> executeApplicationProcessing(String dataId, ServiceMeta service, UserSessionObject user, ProcessingTxn txnLog, String appId
                                                                    , String appStatus) {

            applicationFlowLogs.info("Finalizing application for txnId {} applicationId {} status {} taskType {}",
                                        txnLog.getTxnId(),appId,appStatus,service.getTaskType());

            if(service.getTaskType().equals(OFFICIAL_TASK_FLAG)){
                return saveTxn(txnLog,dataId,service,user,"",appId,txnLog.getTxnId(),"","",appStatus);
            }
            else{
                return reactiveApiClient.fetchReferenceAbbrviation(service.getServiceId(),user,txnLog.getTxnId())
                        .flatMap(data -> {
                            try {
                                JSONObject json = new JSONObject(data);
                                String abbr = json.getString("abbr");

                                applicationFlowLogs.info("Abbreviation for txnId {} is {} ",txnLog.getTxnId(),abbr);

                                String referenceNo = abbr.concat("/").concat(String.valueOf(Year.now().getValue())).concat("/").concat(txnLog.getTxnId());

                                return saveTxn(txnLog,dataId,service,user,referenceNo,appId,txnLog.getTxnId(),"","",appStatus);

                            } catch (Exception e) {
                                return Mono.error(new SPRuntimeError(
                                        "Issue while processing the request [SUB - 009]",
                                        HttpStatus.INTERNAL_SERVER_ERROR,txnLog.getTxnId()
                                ));
                            }
                        })
                        .onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex,txnLog.getTxnId()));
            }


    }

    private Mono<ServerResponse> saveTxn(ProcessingTxn txnLog, String dataId, ServiceMeta service, UserSessionObject user, String referenceNo, String applicationId,
                                              String currentTxnId, String previousTxnId, String previousTaskId, String appStatus) {
        txnLog.setEndTime(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
        txnLog.setNewEntity(false);
        HandlerResponse hr = new HandlerResponse();
        hr.setData(Map.of("referenceNo", referenceNo));
        hr.setApplicationId(applicationId);
        hr.setActivityEnd(true);
        //return transactionalOperator.execute(status ->
        return saveApplicationAndCurrentProcess(
                txnLog, service, user, referenceNo, applicationId, appStatus,dataId
        ).then(ServerResponse.ok().bodyValue(hr));
    }

    private Mono<?> saveApplicationAndCurrentProcess(ProcessingTxn savedLog, ServiceMeta service, UserSessionObject user, String referenceNo, String appId, String appStatus, String dataId) {

        return applicationDetailsRepository.findByApplicationIdAndTenantId(appId,user.getTenantId()).flatMap(ad ->{
            final Integer status = isEmpty(appStatus) ? FALLBACK_ACTION_NO : Integer.parseInt(appStatus);

            if(service.getTaskType().equals(APPLICATION_SUBMISSION_TASK_FLAG)){
                    ad.setReferenceNo(referenceNo);
                    ad.setStatus("I");
                    ad.setAppliedLocationId(service.getSelectedLocationByUser().intValue());
                    ad.setAppliedLocationName(service.getSelectedLocationNameByUser());
            } else if (service.getTaskType().equals(OFFICIAL_TASK_FLAG)) {
                    ad.setStatus(ACTION_CODE_MAPPING.get(status));
            }

            ad.setNewEntity(false);

            return saveCurrentProcess(ad,savedLog,service,user,status,dataId);
        });

    }

    private Mono<?> saveCurrentProcess(ApplicationDetails ad, ProcessingTxn savedLog,
                                       ServiceMeta service, UserSessionObject user, Integer appStatus, String dataId) {
        return currentProcessRepository.findByServiceIdAndApplicationIdAndCurrentTaskAndActionTakenAndTenantId(
                        service.getServiceId(),ad.getApplicationId(),service.getTaskId(),"N", user.getTenantId()
                )
                .switchIfEmpty(
                        createCurrentProcess(ad,service,user)
                )
                .flatMap(cp -> {
                    cp.setActionTaken("Y");
                    cp.setActionOn(LocalDateTime.now());
                    cp.setDataId(dataId);
                    cp.setUserId(user.getUserID());
                    cp.setFormId(service.getFormId());

                    if (service.getSelectedWorkflowElementData() != null
                          && service.getSelectedWorkflowElementData().getActionAttribute() != null) {

                        ServiceProcessFlowDTO.Data.ActionAttribute action =
                                service.getSelectedWorkflowElementData()
                                        .getActionAttribute()
                                        .getFirst();

                        boolean logicalClosure = Boolean.TRUE.equals(action.getLogicalClosure());
                        boolean completeClosure = Boolean.TRUE.equals(action.getCompleteClosure());

                        if (completeClosure) {
                            return transactionalDBExecutor
                                    .execute(savedLog.getTxnId(), cp, ad, savedLog)
                                    .then(sendCurrentProcessToTracking(cp, ad, service, user));
                        }
                    }

                    return workflowService.generateNextWorkflow(ad,savedLog,service,user,cp).
                            flatMap(inboxKafka -> persistWorkflow(ad, (InboxKafka) inboxKafka, savedLog)
                                    .doOnSuccess(_ -> sendToInboxService((InboxKafka) inboxKafka,ad,service)));
                });
    }

    private Mono<Void> sendCurrentProcessToTracking(CurrentProcess currentProcess,
                                                    ApplicationDetails application,
                                                    ServiceMeta service,
                                                    UserSessionObject user) {

        InboxKafka inboxKafka = new InboxKafka();

        inboxKafka.setProcessList(List.of(currentProcess));
        inboxKafka.setOfficeDetails(Collections.emptyList());

        inboxKafka.setServiceName(service.getServiceName());
        inboxKafka.setApplicationRefNo(application.getReferenceNo());

        inboxKafka.setAppliedBy(application.getBeneficiaryId());
        inboxKafka.setBeneficiaryName(application.getBeneficiaryName());
        inboxKafka.setApplyDate(application.getApplyDate());

        inboxKafka.setLoggedInUserId(user.getUserID());
        inboxKafka.setLoggedInUserLocation(user.getLocationId());

        String key = application.getApplicationId()
                .concat("_")
                .concat(UUID.randomUUID().toString());

        kafkaProducer.sendMessage(
                PUSH_FORM_SUBMISSION_DATA_INBOX_TOPIC,
                key,
                entityToString(inboxKafka));

        applicationFlowLogs.info(
                "Terminal action. Current process pushed to tracking. applicationId={}, processId={}",
                application.getApplicationId(),
                currentProcess.getProcessId());

        return Mono.empty();
    }

    private Mono<? extends CurrentProcess> createCurrentProcess(ApplicationDetails ad, ServiceMeta service, UserSessionObject user) {
        CurrentProcess currentProcess = new CurrentProcess();
        currentProcess.setProcessId(createUniqueId());
        currentProcess.setPreviousProcessId("");
        currentProcess.setCurrentTask(service.getTaskId());
        currentProcess.setPreviousTask("");
        currentProcess.setServiceId(service.getServiceId());
        currentProcess.setApplicationId(ad.getApplicationId());
        currentProcess.setCurrentTaskName("");
        currentProcess.setPreviousTaskName("");
        currentProcess.setTenantId(user.getTenantId());
        currentProcess.setBaseServiceId(service.getBaseServiceId());
        currentProcess.setInitiatedOn(LocalDateTime.now());
        currentProcess.setApplicantTask(Boolean.TRUE);

        return Mono.just(currentProcess);
    }

    private Mono<Void> persistWorkflow(
            ApplicationDetails ad,
            InboxKafka inboxKafka,
            ProcessingTxn txn) {

        return transactionalDBExecutor.execute(txn.getTxnId(),inboxKafka.getProcessList(), ad, txn);
    }


    private Mono<Object> sendToInboxService(InboxKafka inboxKafka, ApplicationDetails appDetails,ServiceMeta service) {
        String key = appDetails.getApplicationId().concat("_").concat(UUID.randomUUID().toString());
        inboxKafka.setApplicationRefNo(appDetails.getReferenceNo());
        kafkaProducer.sendMessage(PUSH_FORM_SUBMISSION_DATA_INBOX_TOPIC,key,entityToString(inboxKafka));
        return Mono.just(true);
    }
}
