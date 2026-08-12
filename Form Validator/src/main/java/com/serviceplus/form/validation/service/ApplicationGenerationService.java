package com.serviceplus.form.validation.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.util.*;

import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.ApplicationDocumentSubmissionRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.kafka.KafkaProducer;

import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.ApplicationConstants.*;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.Utility.*;
import static java.util.Objects.isNull;

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
    
    @Autowired
    private AssociatedTaskService associatedTaskService;
    
    @Autowired
    private WorkflowWebServiceTaskExecutor workflowWebServiceTaskExecutor;

    @Autowired
    private ApplicationDocumentSubmissionRepository applicationDocumentSubmissionRepository;

    @Value("${push.form.submission.data.inbox.topic}")
    private String PUSH_FORM_SUBMISSION_DATA_INBOX_TOPIC;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    public Mono<ServerResponse> executeApplicationProcessing(String dataId, ServiceMeta service, UserSessionObject user, ProcessingTxn txnLog, String appId, String appStatus, String from) {

        applicationFlowLogs.info("Finalizing application for txnId {} applicationId {} status {} taskType {}", txnLog.getTxnId(), appId, appStatus, service.getTaskType());

        return applicationDetailsRepository
                .findByApplicationIdAndTenantId(
                        appId,
                        user.getTenantId())
                .switchIfEmpty(Mono.error(
                        new SPRuntimeError("Application details not found.", HttpStatus.BAD_REQUEST, txnLog.getTxnId()
                        )
                ))
                .flatMap(applicationDetails -> {

                    String referenceNo = applicationDetails.getReferenceNo();

                    applicationFlowLogs.info("Using existing referenceNo {} for applicationId {} txnId {}", referenceNo, appId, txnLog.getTxnId());

                    return saveTxn(txnLog, dataId, service, user, referenceNo, appId, txnLog.getTxnId(), "", "", appStatus, from);
                });
    }

    private Mono<ServerResponse> saveTxn(ProcessingTxn txnLog, String dataId, ServiceMeta service, UserSessionObject user, String referenceNo, String applicationId,
                                         String currentTxnId, String previousTxnId, String previousTaskId, String appStatus, String from) {
        txnLog.setEndTime(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
        txnLog.setNewEntity(false);
        HandlerResponse hr = new HandlerResponse();

        hr.setApplicationId(applicationId);
        hr.setActivityEnd(true);
        hr.setActivityType(from);
        //return transactionalOperator.execute(status ->
        return saveApplicationAndCurrentProcess(
                txnLog, service, user, referenceNo, applicationId, appStatus,dataId,hr
        ).then(ServerResponse.ok().bodyValue(hr));
    }

    private Mono<?> saveApplicationAndCurrentProcess(ProcessingTxn savedLog, ServiceMeta service, UserSessionObject user, String referenceNo, String appId, String appStatus, String dataId, HandlerResponse hr) {

        return applicationDetailsRepository.findByApplicationIdAndTenantId(appId,user.getTenantId()).flatMap(ad ->{
            final Integer status = isEmpty(appStatus) ? FALLBACK_ACTION_NO : Integer.parseInt(appStatus);
            Map<String,Object> result = new HashMap<>();
            result.put("data",service.getPreviousHandlerData());
            hr.setData(result);

            if(service.getTaskType().equals(APPLICATION_SUBMISSION_TASK_FLAG)){
                    ad.setReferenceNo(referenceNo);
                    ad.setStatus("I");

                    if(!isNull(service.getSelectedLocationByUser())){
                        ad.setAppliedLocationId(service.getSelectedLocationByUser().intValue());
                    }

                    if(!isNull(service.getSelectedLocationNameByUser())) {
                        ad.setAppliedLocationName(service.getSelectedLocationNameByUser());
                    }
                    result.put("referenceNo", referenceNo);
            } else if (service.getTaskType().equals(OFFICIAL_TASK_FLAG)) {
                    ad.setStatus(ACTION_CODE_MAPPING.get(status));
                    result.put("referenceNo", ad.getReferenceNo());
            }

            ad.setNewEntity(false);

            return saveCurrentProcess(ad,savedLog,service,user,status,dataId);
        });

    }

    private Mono<?> saveCurrentProcess(ApplicationDetails ad,
                                       ProcessingTxn savedLog,
                                       ServiceMeta service,
                                       UserSessionObject user,
                                       Integer appStatus,
                                       String dataId) {

        return currentProcessRepository
                .findByServiceIdAndApplicationIdAndCurrentTaskAndActionTakenAndTenantId(
                        service.getServiceId(),
                        ad.getApplicationId(),
                        service.getTaskId(),
                        "N",
                        user.getTenantId())
                .switchIfEmpty(createCurrentProcess(ad, service, user))
                .flatMap(cp -> {

                    cp.setActionTaken("Y");
                    cp.setActionOn(LocalDateTime.now());
                    cp.setDataId(dataId);
                    cp.setUserId(user.getUserID());
                    cp.setFormId(service.getFormId());
                    ServiceProcessFlowDTO.Data.WorkflowElementData selectedWorkflowElementData = service.getSelectedWorkflowElementData();

                    cp.setActionCode(Integer.parseInt(selectedWorkflowElementData.getActionAttribute().getFirst().getKey()));
                    cp.setActionName(selectedWorkflowElementData.getActionAttribute().getFirst().getTrackLabel());

                    Mono<CurrentProcess> populateDocuments =
                            reactiveApiClient
                                    .fetchServiceMetadata(
                                            user,
                                            service.getServiceId(),
                                            savedLog.getTxnId()
                                    )
                                    .flatMap(metadataService -> {

                                        List<DocumentGenerationDetails.DocMappingDTO> documentMappings =
                                                metadataService.getDocumentGenerationDetails() == null
                                                        ? Collections.emptyList()
                                                        : metadataService.getDocumentGenerationDetails()
                                                        .stream()
                                                        .filter(details ->
                                                                service.getTaskId().equals(details.getTaskId()))
                                                        .findFirst()
                                                        .map(DocumentGenerationDetails::getDocumentMapping)
                                                        .orElse(Collections.emptyList());

                                        return applicationDocumentSubmissionRepository
                                                .findByApplicationIdAndTxnIdAndTaskIdAndStatus(
                                                        ad.getApplicationId(),
                                                        savedLog.getTxnId(),
                                                        service.getTaskId(),
                                                        "P")
                                                .map(entity -> {

                                                    TrackingDocument document = new TrackingDocument();

                                                    document.setUploadId(entity.getUploadId());
                                                    document.setReferenceId(entity.getReferenceId());
                                                    document.setDocumentName(entity.getDocumentName());

                                                    // Find metadata using document referenceId
                                                    documentMappings.stream()
                                                            .filter(mapping ->
                                                                    mapping.getReferenceId() != null
                                                                            && mapping.getReferenceId()
                                                                            .equals(entity.getReferenceId()))
                                                            .findFirst()
                                                            .ifPresent(mapping -> {

                                                                document.setViewPermission(
                                                                        mapping.getViewPermission()
                                                                );

                                                                applicationFlowLogs.info(
                                                                        "TxnId : {} | Document referenceId: {} | ViewPermission: {}",
                                                                        savedLog.getTxnId(),
                                                                        entity.getReferenceId(),
                                                                        mapping.getViewPermission()
                                                                );
                                                            });

                                                    return document;
                                                })
                                                .collectList()
                                                .map(documents -> {
                                                    cp.setDocuments(documents);
                                                    return cp;
                                                });
                                    });

                    return populateDocuments.flatMap(currentProcess -> {

                        if (service.getSelectedWorkflowElementData() != null
                                && service.getSelectedWorkflowElementData().getActionAttribute() != null) {

                            ServiceProcessFlowDTO.Data.ActionAttribute action = service.getSelectedWorkflowElementData()
                                                                                       .getActionAttribute()
                                                                                       .getFirst();

                            boolean completeClosure =  Boolean.TRUE.equals(action.getCompleteClosure());

                            if (completeClosure) {

                                return transactionalDBExecutor
                                        .execute(
                                                savedLog.getTxnId(), currentProcess, ad, savedLog
                                        )
                                        .then(sendCurrentProcessToTracking(
                                                currentProcess, ad, service, user,completeClosure)
                                        );
                            }
                        }

                        return workflowService.generateNextWorkflow(
                                        ad,
                                        savedLog,
                                        service,
                                        user,
                                        currentProcess)
                                .flatMap(inboxKafka ->
                                        persistWorkflow(ad, (InboxKafka) inboxKafka, savedLog, user
                                        )
                                        .doOnSuccess(_ ->
                                                        sendToInboxService((InboxKafka) inboxKafka, ad, service))
                                );
                    });
                });
    }

    public Mono<Void> sendCurrentProcessToTracking(CurrentProcess currentProcess,
                                                    ApplicationDetails application,
                                                    ServiceMeta service,
                                                    UserSessionObject user, boolean completeClosure) {

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
        
        inboxKafka.setCompleteClosure(completeClosure);

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
        currentProcess.setActionCode(FALLBACK_ACTION_NO);

        return Mono.just(currentProcess);
    }

    public Mono<Void> persistWorkflow(
            ApplicationDetails ad,
            InboxKafka inboxKafka,
            ProcessingTxn txn,
            UserSessionObject user) {

        return transactionalDBExecutor.execute(txn.getTxnId(),inboxKafka.getProcessList(), ad, txn)
        		.then(Mono.fromRunnable(() ->
                associatedTaskService.executeAssociatedTasks(
                        inboxKafka.getProcessList(),
                        ad,
                        user)))
        .then(workflowWebServiceTaskExecutor.execute(
                inboxKafka.getProcessList(),
                ad,
                user)).then();
    }


    public Mono<Object> sendToInboxService(InboxKafka inboxKafka, ApplicationDetails appDetails,ServiceMeta service) {
        String key = appDetails.getApplicationId().concat("_").concat(UUID.randomUUID().toString());
        inboxKafka.setApplicationRefNo(appDetails.getReferenceNo());
        kafkaProducer.sendMessage(PUSH_FORM_SUBMISSION_DATA_INBOX_TOPIC,key,entityToString(inboxKafka));
        return Mono.just(true);
    }
}
