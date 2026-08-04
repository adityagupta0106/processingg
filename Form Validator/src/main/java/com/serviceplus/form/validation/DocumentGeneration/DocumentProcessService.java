package com.serviceplus.form.validation.DocumentGeneration;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.ApplicationDocumentLogEntity;
import com.serviceplus.form.validation.entity.ApplicationDocumentSubmissionEntity;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.enums.DocumentMode;
import com.serviceplus.form.validation.flow.EventDecider;
import com.serviceplus.form.validation.repository.ApplicationDocumentLogRepository;
import com.serviceplus.form.validation.repository.ApplicationDocumentSubmissionRepository;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;
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
import java.util.List;
import java.util.Map;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_STATUS_KEY;
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

    public DocumentProcessService(ReactiveApiClient reactiveApiClient, DocumentGenerationExecutor documentGenerationExecutor, ApplicationFlowRouterRepository applicationFlowRouterRepository, EventDecider eventDecider, ProcessingTxnRepository processingTxnRepository, ApplicationDocumentSubmissionRepository applicationDocumentSubmissionRepository, ApplicationDocumentLogRepository applicationDocumentLogRepository, ActivityMapService activityMapService) {
        this.reactiveApiClient = reactiveApiClient;
        this.documentGenerationExecutor = documentGenerationExecutor;
        this.applicationFlowRouterRepository = applicationFlowRouterRepository;
        this.eventDecider = eventDecider;
        this.processingTxnRepository = processingTxnRepository;
        this.applicationDocumentSubmissionRepository = applicationDocumentSubmissionRepository;
        this.applicationDocumentLogRepository = applicationDocumentLogRepository;
        this.activityMapService = activityMapService;
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

                                                List<Map<String, Object>> selectedActions =
                                                        (List<Map<String, Object>>) workflowData.get("action");

                                                if (selectedActions == null || selectedActions.isEmpty()) {
                                                    return Mono.error(new SPRuntimeError("No action selected.", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                                                }

                                                if (service.getDocumentGenerationDetails() == null || service.getDocumentGenerationDetails().getDocumentMapping() == null) {
                                                    return Mono.error(new SPRuntimeError("Document generation configuration not found.", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                                                }

                                                String actionCode =  String.valueOf(selectedActions.getFirst().get("key"));

                                                List<DocumentGenerationDetails.DocMappingDTO> applicableMappings =
                                                        service.getDocumentGenerationDetails()
                                                                .getDocumentMapping()
                                                                .stream()
                                                                .filter(mapping ->
                                                                        mapping.getAction() != null &&
                                                                                mapping.getAction()
                                                                                        .stream()
                                                                                        .anyMatch(action ->
                                                                                                actionCode.equals(action.getValue())))
                                                                .toList();

                                                if (applicableMappings.isEmpty()) {
                                                    return Mono.error(new SPRuntimeError("No document mapping configured for action : " + actionCode, HttpStatus.BAD_REQUEST, flow.getTxnId()));
                                                }

                                                applicationFlowLogs.info("TxnId : {} | Action : {} | Applicable Documents : {}", flow.getTxnId(), actionCode, applicableMappings.size());

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

                                                } else {

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

                                                                            if (mode == DocumentMode.FETCH && userSubmissionRequired) {

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
                                                                                                    actionCode,
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

        return Flux.fromIterable(documentSections)
                .flatMap(section ->
                        Flux.fromIterable(section.getDocuments())
                                .flatMap(document -> {

                                    Mono<ApplicationDocumentLogEntity> logMono;

                                    if ("fileUpload".equalsIgnoreCase(document.getSourceType())) {

                                        String processId = isNull(flow.getCurrentProcess()) ? null : flow.getCurrentProcess().getProcessId();
                                        ApplicationDocumentLogEntity log = new ApplicationDocumentLogEntity();

                                        log.setNewEntity(Boolean.TRUE);
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

                                        logMono = applicationDocumentLogRepository.save(log);

                                    } else {

                                        logMono = applicationDocumentLogRepository
                                                .findById(document.getDocumentId())
                                                .switchIfEmpty(
                                                        Mono.error(new SPRuntimeError("Document log not found for documentId : " + document.getDocumentId(), HttpStatus.BAD_REQUEST, flow.getTxnId()
                                                        ))
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

                                        entity.setMerged(Boolean.TRUE.equals(section.getMergeRequired()));
                                        entity.setStatus("P");
                                        entity.setCreatedBy(user.getUserID());
                                        entity.setCreatedOn(LocalDateTime.now());
                                        entity.setNew(Boolean.TRUE);

                                        return entity;
                                    });
                                })
                )
                .collectList()
                .flatMapMany(applicationDocumentSubmissionRepository::saveAll)
                .then();
    }
}
