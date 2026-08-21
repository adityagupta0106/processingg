package com.serviceplus.form.validation.DocumentGeneration;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.*;
import com.serviceplus.form.validation.enums.DocumentMode;
import com.serviceplus.form.validation.flow.EventDecider;
import com.serviceplus.form.validation.repository.*;
import com.serviceplus.form.validation.service.ActivityMapService;
import com.serviceplus.form.validation.service.ReactiveApiClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_STATUS_KEY;
import static com.serviceplus.form.validation.utility.ApplicationConstants.FALLBACK_ACTION_NO;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static java.util.Objects.isNull;

@Service
public class DocumentProcessService {

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    private final ReactiveApiClient reactiveApiClient;

    private final DocumentGenerationExecutor documentGenerationExecutor;

    private final ApplicationFlowRouterRepository applicationFlowRouterRepository;

    private final EventDecider eventDecider;

    private final ProcessingTxnRepository processingTxnRepository;

    private final ApplicationDocumentSubmissionRepository applicationDocumentSubmissionRepository;

    private final ApplicationDocumentLogRepository applicationDocumentLogRepository;

    private final ActivityMapService activityMapService;

    private final ApplicationDocumentMergeRepository applicationDocumentMergeRepository;

    private final ApplicationDocumentLogRepository applicationDocumentRepository;

    public DocumentProcessService(ReactiveApiClient reactiveApiClient, DocumentGenerationExecutor documentGenerationExecutor, ApplicationFlowRouterRepository applicationFlowRouterRepository, EventDecider eventDecider, ProcessingTxnRepository processingTxnRepository, ApplicationDocumentSubmissionRepository applicationDocumentSubmissionRepository, ApplicationDocumentLogRepository applicationDocumentLogRepository, ActivityMapService activityMapService, ApplicationDocumentMergeRepository applicationDocumentMergeRepository, ApplicationDocumentLogRepository applicationDocumentRepository) {
        this.reactiveApiClient = reactiveApiClient;
        this.documentGenerationExecutor = documentGenerationExecutor;
        this.applicationFlowRouterRepository = applicationFlowRouterRepository;
        this.eventDecider = eventDecider;
        this.processingTxnRepository = processingTxnRepository;
        this.applicationDocumentSubmissionRepository = applicationDocumentSubmissionRepository;
        this.applicationDocumentLogRepository = applicationDocumentLogRepository;
        this.activityMapService = activityMapService;
        this.applicationDocumentMergeRepository = applicationDocumentMergeRepository;
        this.applicationDocumentRepository = applicationDocumentRepository;
    }

    @SuppressWarnings("unchecked")
    public Mono<ServerResponse> process(String applicationId,
                                        ServerRequest request,
                                        String statusKey,
                                        String txnId,
                                        Mono<TempTransactionLogs> tempLogs,
                                        ApplicationFlowStatusEntity flow,
                                        ServiceMeta service,
                                        boolean fromDraft,
                                        DocumentGenerationRequest body,
                                        DocumentMode mode) {

        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
        txnId = flow.getTxnId();

        return reactiveApiClient
                .fetchServiceMetadata(user, service.getServiceId(), txnId)
                .flatMap(metadata -> {

                    DocumentGenerationDetails dgConfig = null;

                    if (!isNull(metadata.getDocumentGenerationDetails())) {

                        dgConfig = metadata.getDocumentGenerationDetails()
                                .stream()
                                .filter(d -> service.getTaskId().equals(d.getTaskId()))
                                .findFirst()
                                .orElse(null);
                    }

                    if (isNull(dgConfig)) {
                        return Mono.error(new RuntimeException("No document generation configuration found for task : " + service.getTaskId()));
                    }

                    service.setDocumentGenerationDetails(dgConfig);

                    return applicationFlowRouterRepository
                            .findFirstByApplicationIdAndTaskIdAndActivityTypeAndCompletedOrderByIdDesc(
                                    flow.getApplicationId(),
                                    flow.getTaskId(),
                                    ACTIVITY_FORM_STATUS_KEY,
                                    1)
                            .switchIfEmpty(Mono.error(new SPRuntimeError("Completed Form Submission activity not found.", HttpStatus.BAD_REQUEST, flow.getTxnId())))
                            .flatMap(fsFlow ->

                                    reactiveApiClient
                                            .fetchWorkflowAttributes(
                                                    user,
                                                    fsFlow.getFormId(),
                                                    fsFlow.getDataId(),
                                                    fsFlow.getTxnId())
                                            .flatMap(workflowData -> {

                                                Map<String, Object> selectedAction = (Map<String, Object>) workflowData.get("action");

                                                if (service.getDocumentGenerationDetails() == null || service.getDocumentGenerationDetails().getDocumentMapping() == null) {
                                                    return Mono.error(new SPRuntimeError("Document generation configuration not found.", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                                                }

                                                List<DocumentGenerationDetails.DocMappingDTO> applicableMappings;

                                                List<DocumentGenerationDetails.DocMappingDTO> mappings = service.getDocumentGenerationDetails().getDocumentMapping();

                                                String actionCode = FALLBACK_ACTION_NO.toString();

                                                if (selectedAction == null || selectedAction.isEmpty()) {

                                                    Set<String> configuredActionCodes = metadata.getWorkFlowDetails()
                                                                                        .stream()
                                                                                        .filter(workflow ->
                                                                                                service.getTaskId().equals(workflow.getTaskId()))
                                                                                        .flatMap(workflow ->
                                                                                                workflow.getAllowedAction().stream())
                                                                                        .map(action -> String.valueOf(action.getActionKey()))
                                                                                        .filter(Objects::nonNull)
                                                                                        .collect(Collectors.toSet());

                                                    if (configuredActionCodes.size() == 1) {

                                                        actionCode = configuredActionCodes.iterator().next();

                                                        String ac_1 = actionCode;

                                                        applicableMappings = mappings.stream()
                                                                                .filter(mapping ->
                                                                                        mapping.getAction() != null
                                                                                                && mapping.getAction().stream()
                                                                                                .anyMatch(action ->
                                                                                                        ac_1.equals(action.getValue())))
                                                                                .toList();

                                                        applicationFlowLogs.info("TxnId : {} | No action selected. Using configured actionCode={}", flow.getTxnId(), actionCode);

                                                    } else {
                                                        return Mono.error(new SPRuntimeError("No action selected [DOC - 01].", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                                                    }


                                                } else {

                                                    actionCode = String.valueOf(selectedAction.get("value"));
                                                    String aC_1 = actionCode;

                                                    applicableMappings = service.getDocumentGenerationDetails()
                                                                                .getDocumentMapping()
                                                                                .stream()
                                                                                .filter(mapping ->
                                                                                        mapping.getAction() != null &&
                                                                                                mapping.getAction()
                                                                                                        .stream()
                                                                                                        .anyMatch(action ->
                                                                                                                aC_1.equals(action.getValue())))
                                                                                .toList();

                                                    if (applicableMappings.isEmpty()) {

                                                        applicationFlowLogs.info("TxnId : {} | Selected action {} has no document mapping. " + "Skipping document generation and proceeding to next workflow.", flow.getTxnId(), actionCode);

                                                        String ac_3 = actionCode;

                                                        return processingTxnRepository
                                                                .findById(flow.getTxnId())
                                                                .switchIfEmpty(Mono.error(
                                                                        new SPRuntimeError("Transaction not found.", HttpStatus.BAD_REQUEST, flow.getTxnId())
                                                                ))
                                                                .flatMap(txnLog -> {

                                                                    applicationFlowLogs.info("TxnId : {} | No document mapping for selected action {}. " + "Proceeding to next workflow.", flow.getTxnId(), ac_3
                                                                    );

                                                                    return eventDecider.proceedToNext(
                                                                            fsFlow.getDataId(),
                                                                            service,
                                                                            user,
                                                                            txnLog,
                                                                            applicationId,
                                                                            ac_3,
                                                                            flow.getActivityType(),
                                                                            request,
                                                                            flow
                                                                    );
                                                                });

                                                    }

                                                    applicationFlowLogs.info("TxnId : {} | Action : {} | Applicable Documents : {}", flow.getTxnId(), actionCode, applicableMappings.size());
                                                }

                                                Mono<List<DocumentSectionResponse>> documentProcess;

                                                if (mode == DocumentMode.FETCH) {

                                                    documentProcess = documentGenerationExecutor.execute(
                                                            applicationId,
                                                            flow.getTxnId(),
                                                            user,
                                                            flow,
                                                            service,
                                                            applicableMappings,
                                                            fromDraft);

                                                } else if (mode == DocumentMode.MERGE) {

                                                    documentProcess =
                                                            saveUploadedDocumentsForMerge(
                                                                        applicationId,
                                                                        flow,
                                                                        service,
                                                                        body,
                                                                        user
                                                                    )
                                                                    .then(
                                                                            documentGenerationExecutor.merge(
                                                                                    applicationId,
                                                                                    flow.getTxnId(),
                                                                                    user,
                                                                                    flow,
                                                                                    service,
                                                                                    applicableMappings,
                                                                                    body,
                                                                                    fromDraft)
                                                                    );
                                                }else {

                                                    documentProcess = documentGenerationExecutor.submit(
                                                            applicationId,
                                                            flow.getTxnId(),
                                                            user,
                                                            flow,
                                                            service,
                                                            applicableMappings,
                                                            body,
                                                            fromDraft);
                                                }

                                                String ac_2 = actionCode;

                                                return documentProcess
                                                        .flatMap(result ->

                                                                activityMapService.isUserSubmissionRequired(
                                                                                service,
                                                                                user,
                                                                                applicationId,
                                                                                flow.getTxnId(),
                                                                                flow.getActivityType()
                                                                        )
                                                                        .flatMap(userSubmissionRequired -> {

                                                                            applicationFlowLogs.info(
                                                                                    "TxnId : {} | Document processing completed successfully. Total Sections : {} userSubmissionRequired {}",
                                                                                    flow.getTxnId(),
                                                                                    result.size(),userSubmissionRequired);

                                                                            if (mode == DocumentMode.MERGE || (mode == DocumentMode.FETCH && userSubmissionRequired)) {

                                                                                HandlerResponse response = new HandlerResponse();
                                                                                response.setTxnId(flow.getTxnId());
                                                                                response.setApplicationId(applicationId);
                                                                                response.setActivityType(flow.getActivityType());
                                                                                response.setActivityEnd(Boolean.FALSE);
                                                                                response.setWorkflowElementData(service.getSelectedWorkflowElementData());
                                                                                response.setData(Map.of("documentSections", result));

                                                                                applicationFlowLogs.info("TxnId : {} | Returning document generation response to UI.", flow.getTxnId());

                                                                                return ServerResponse.ok().bodyValue(response);
                                                                            }

                                                                            service.setPreviousHandlerData(Map.of("documentSections", result));

                                                                            return saveGeneratedDocuments(
                                                                                    applicationId,
                                                                                    flow,
                                                                                    service,
                                                                                    result,
                                                                                    true,
                                                                                    user
                                                                            )
                                                                                    .then(processingTxnRepository.findById(flow.getTxnId()))
                                                                                    .switchIfEmpty(Mono.error(new SPRuntimeError(
                                                                                            "Processing transaction not found.",
                                                                                            HttpStatus.INTERNAL_SERVER_ERROR,
                                                                                            flow.getTxnId()
                                                                                    )))
                                                                                    .flatMap(txnLog ->{

                                                                                        applicationFlowLogs.info("TxnId : {} | Invoking EventDecider for next workflow activity.", flow.getTxnId());


                                                                                            return eventDecider.proceedToNext(
                                                                                                    fsFlow.getDataId(),
                                                                                                    service,
                                                                                                    user,
                                                                                                    txnLog,
                                                                                                    applicationId,
                                                                                                    ac_2,
                                                                                                    flow.getActivityType(),
                                                                                                    request,
                                                                                                    flow
                                                                                            );
                                                                                         });
                                                                        }));
                                            })
                            );
                });
    }

    public Mono<Void> saveGeneratedDocuments(
            String applicationId,
            ApplicationFlowStatusEntity flow,
            ServiceMeta service,
            List<DocumentSectionResponse> documentSections,
            boolean permanent,
            UserSessionObject user) {

        applicationFlowLogs.info(
                "TxnId : {} | saveGeneratedDocuments START | ApplicationId: {} | Total Sections: {}",
                flow.getTxnId(),
                applicationId,
                documentSections.size()
        );

        return Flux.fromIterable(documentSections)
                .doOnNext(section ->
                        applicationFlowLogs.info("TxnId : {} | Processing Section | ReferenceId: {} | DocumentName: {} | MergeRequired: {} | Documents: {}",
                                flow.getTxnId(),
                                section.getReferenceId(),
                                section.getDocumentName(),
                                section.getMergeRequired(),
                                section.getDocuments() == null
                                        ? 0
                                        : section.getDocuments().size()
                        )
                )
                .flatMap(section -> {

                    if (Boolean.TRUE.equals(section.getMergeRequired())) {

                        return findActiveMergeEntity(
                                applicationId,
                                flow,
                                service,
                                section.getReferenceId(),
                                user
                        )
                                .doOnNext(mergeEntity ->
                                        applicationFlowLogs.info(
                                                "TxnId : {} | Using merged document for submission | " +
                                                        "MergeId: {} | ReferenceId: {} | MergedUploadId: {} | Status: {}",
                                                flow.getTxnId(),
                                                mergeEntity.getId(),
                                                mergeEntity.getReferenceId(),
                                                mergeEntity.getMergedUploadId(),
                                                mergeEntity.getStatus()
                                        )
                                )
                                .map(mergeEntity -> {

                                    ApplicationDocumentSubmissionEntity entity = new ApplicationDocumentSubmissionEntity();

                                    entity.setId(createUniqueId());
                                    entity.setTxnId(flow.getTxnId());
                                    entity.setApplicationId(applicationId);
                                    entity.setServiceId(service.getServiceId());
                                    entity.setTaskId(service.getTaskId());

                                    entity.setReferenceId(mergeEntity.getReferenceId());
                                    entity.setDocumentName(section.getDocumentName());
                                    entity.setProcessId(mergeEntity.getProcessId());
                                    entity.setUploadId(mergeEntity.getMergedUploadId());
                                    entity.setSignedUploadId("");
                                    entity.setMerged(Boolean.TRUE);
                                    entity.setStatus("P");
                                    entity.setCreatedBy(user.getUserID());
                                    entity.setCreatedOn(LocalDateTime.now());
                                    entity.setNew(Boolean.TRUE);
                                    entity.setTenantId(user.getTenantId());

                                    applicationFlowLogs.info("TxnId : {} | Creating merged submission | " + "ReferenceId: {} | UploadId: {} | ProcessId: {}", flow.getTxnId(), entity.getReferenceId(), entity.getUploadId(), entity.getProcessId());

                                    return entity;
                                });
                    }

                    return Flux.fromIterable(section.getDocuments())
                            .flatMap(document -> {

                                Mono<ApplicationDocumentLogEntity> logMono;

                                if ("fileUpload".equalsIgnoreCase(document.getSourceType())) {

                                    String processId = isNull(flow.getCurrentProcess()) ? null : flow.getCurrentProcess().getProcessId();

                                    ApplicationDocumentLogEntity log = new ApplicationDocumentLogEntity();

                                    log.setNewEntity(true);
                                    log.setId(createUniqueId());
                                    log.setTxnId(flow.getTxnId());
                                    log.setApplicationId(applicationId);
                                    log.setServiceId(service.getServiceId());
                                    log.setTaskId(service.getTaskId());
                                    log.setProcessId(processId);

                                    log.setReferenceId(document.getReferenceId());

                                    log.setDocumentName(document.getDocumentName());

                                    log.setSourceType("fileUpload");
                                    log.setUploadId(document.getUploadId());
                                    log.setStatus("P");
                                    log.setCreatedOn(LocalDateTime.now());
                                    log.setTenantId(user.getTenantId());

                                    logMono = applicationDocumentLogRepository.save(log);

                                } else {

                                    logMono =
                                            applicationDocumentLogRepository
                                                    .findById(
                                                            document.getDocumentId()
                                                    )
                                                    .switchIfEmpty(
                                                            Mono.error(
                                                                    new SPRuntimeError(
                                                                            "Document log not found for documentId : "
                                                                                    + document.getDocumentId(),
                                                                            HttpStatus.BAD_REQUEST,
                                                                            flow.getTxnId()
                                                                    )
                                                            )
                                                    );
                                }

                                return logMono.map(log -> {

                                    ApplicationDocumentSubmissionEntity entity = new ApplicationDocumentSubmissionEntity();

                                    entity.setId(createUniqueId());
                                    entity.setTxnId(flow.getTxnId());
                                    entity.setApplicationId(applicationId);
                                    entity.setServiceId(service.getServiceId());
                                    entity.setTaskId(service.getTaskId());

                                    entity.setReferenceId(log.getReferenceId());
                                    entity.setDocumentName(log.getDocumentName());
                                    entity.setProcessId(log.getProcessId());
                                    entity.setUploadId(log.getUploadId());

                                    entity.setSignedUploadId("");
                                    entity.setMerged(false);
                                    entity.setStatus("P");

                                    entity.setCreatedBy(user.getUserID());
                                    entity.setCreatedOn(LocalDateTime.now());
                                    entity.setNew(true);
                                    entity.setTenantId(user.getTenantId());

                                    return entity;
                                });
                            });
                })
                .collectList()
                .flatMapMany(applicationDocumentSubmissionRepository::saveAll)
                .then()
                .doOnSuccess(v ->
                        applicationFlowLogs.info("TxnId : {} | saveGeneratedDocuments COMPLETED | ApplicationId: {}", flow.getTxnId(), applicationId)
                );
    }

    private Mono<ApplicationDocumentMergeEntity> findActiveMergeEntity(
            String applicationId,
            ApplicationFlowStatusEntity flow,
            ServiceMeta service,
            String referenceId,
            UserSessionObject user) {

        if (flow.getCurrentProcess() != null) {

            return applicationDocumentMergeRepository
                    .findFirstByApplicationIdAndProcessIdAndReferenceIdAndTenantIdAndStatusOrderByCreatedOnDesc(
                            applicationId,
                            flow.getCurrentProcess().getProcessId(),
                            referenceId,
                            user.getTenantId(),
                            "P"
                    );

        }

        return applicationDocumentMergeRepository
                .findFirstByApplicationIdAndTaskIdAndReferenceIdAndTenantIdAndStatusOrderByCreatedOnDesc(
                        applicationId,
                        service.getTaskId(),
                        referenceId,
                        user.getTenantId(),
                        "P"
                );
    }

    private Mono<Void> saveUploadedDocumentsForMerge(
            String applicationId,
            ApplicationFlowStatusEntity flow,
            ServiceMeta service,
            DocumentGenerationRequest body,
            UserSessionObject user) {

        if (body == null
                || body.getMergedUploadSection() == null
                || body.getMergedUploadSection().isEmpty()) {

            return Mono.empty();
        }

        return Flux.fromIterable(body.getMergedUploadSection())
                .flatMap(section -> {

                    if (section.getDocuments() == null
                            || section.getDocuments().isEmpty()) {
                        return Mono.empty();
                    }

                    return Flux.fromIterable(section.getDocuments())
                            .filter(document ->
                                    document.getDocumentId() != null
                                            && !document.getDocumentId().isBlank())
                            .flatMap(document -> {

                                String processId = flow.getCurrentProcess() != null ? flow.getCurrentProcess().getProcessId() : null;

                                Mono<ApplicationDocumentLogEntity> existingDocumentMono;

                                if (flow.getCurrentProcess() != null) {

                                    existingDocumentMono =
                                            applicationDocumentRepository
                                                    .findFirstByApplicationIdAndProcessIdAndReferenceIdAndSourceTypeOrderByCreatedOnDesc(
                                                            applicationId,
                                                            processId,
                                                            document.getReferenceId(),
                                                            "fileUpload"
                                                    );

                                } else {

                                    existingDocumentMono =
                                            applicationDocumentRepository
                                                    .findFirstByApplicationIdAndTaskIdAndReferenceIdAndSourceTypeOrderByCreatedOnDesc(
                                                            applicationId,
                                                            service.getTaskId(),
                                                            document.getReferenceId(),
                                                            "fileUpload"
                                                    );
                                }

                                return existingDocumentMono
                                        .flatMap(existing -> {

                                            if (document.getDocumentId().equals(existing.getUploadId()) && "P".equals(existing.getStatus())) {

                                                applicationFlowLogs.info(
                                                        "TxnId : {} | Same uploaded document already exists. " +
                                                                "Skipping save. ApplicationId: {} | " +
                                                                "ReferenceId: {} | UploadId: {} | Status: {}",
                                                        flow.getTxnId(),
                                                        applicationId,
                                                        document.getReferenceId(),
                                                        document.getDocumentId(),
                                                        existing.getStatus()
                                                );

                                                return Mono.just(existing);
                                            }

                                            applicationFlowLogs.info(
                                                    "TxnId : {} | New upload detected. Replacing old document log. " +
                                                            "ApplicationId: {} | ReferenceId: {} | " +
                                                            "OldUploadId: {} | NewUploadId: {}",
                                                    flow.getTxnId(),
                                                    applicationId,
                                                    document.getReferenceId(),
                                                    existing.getUploadId(),
                                                    document.getDocumentId()
                                            );

                                            existing.setStatus("R");

                                            return applicationDocumentLogRepository
                                                    .save(existing)
                                                    .then(
                                                            saveUploadedDocumentLog(
                                                                    applicationId,
                                                                    flow,
                                                                    service,
                                                                    document,
                                                                    processId,
                                                                    user
                                                            )
                                                    );
                                        })
                                        .switchIfEmpty(
                                                Mono.defer(() ->
                                                        saveUploadedDocumentLog(
                                                                applicationId,
                                                                flow,
                                                                service,
                                                                document,
                                                                processId,
                                                                user
                                                        )
                                                )
                                        );
                            });
                })
                .then();
    }

    private Mono<ApplicationDocumentLogEntity> saveUploadedDocumentLog(String applicationId, ApplicationFlowStatusEntity flow, ServiceMeta service, DocumentGenerationRequest.DocumentSectionRequest.DocumentRequest document, String processId, UserSessionObject user) {

        ApplicationDocumentLogEntity log = new ApplicationDocumentLogEntity();

        log.setNewEntity(Boolean.TRUE);
        log.setId(createUniqueId());

        log.setTxnId(flow.getTxnId());
        log.setApplicationId(applicationId);
        log.setServiceId(service.getServiceId());
        log.setTaskId(service.getTaskId());
        log.setProcessId(processId);

        log.setReferenceId(document.getReferenceId());
        log.setSourceType("fileUpload");
        log.setUploadId(document.getDocumentId());
        log.setStatus("P");
        log.setCreatedOn(LocalDateTime.now());
        log.setTenantId(user.getTenantId());

        service.getDocumentGenerationDetails()
                .getDocumentMapping()
                .stream()
                .filter(mapping ->
                        mapping.getReferenceId() != null && mapping.getReferenceId().equals(document.getReferenceId())
                )
                .findFirst()
                .ifPresent(mapping ->
                        log.setDocumentName(mapping.getDocumentName())
                );

        applicationFlowLogs.info("TxnId : {} | Saving uploaded document. " + "ApplicationId: {} | ReferenceId: {} | UploadId: {} | ProcessId: {}", flow.getTxnId(), applicationId, document.getReferenceId(), document.getDocumentId(), processId);

        return applicationDocumentLogRepository.save(log);
    }
}
