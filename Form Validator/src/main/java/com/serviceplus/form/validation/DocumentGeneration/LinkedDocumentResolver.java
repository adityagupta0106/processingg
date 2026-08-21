package com.serviceplus.form.validation.DocumentGeneration;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.ApplicationDocumentLogEntity;
import com.serviceplus.form.validation.entity.ApplicationDocumentSubmissionEntity;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.repository.ApplicationDocumentLogRepository;
import com.serviceplus.form.validation.repository.ApplicationDocumentSubmissionRepository;
import com.serviceplus.form.validation.service.ReactiveApiClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static java.util.Objects.isNull;

@Service
public class LinkedDocumentResolver {

    private static final String LINKED_DOC = "linked";

    private final ApplicationDocumentSubmissionRepository applicationDocumentSubmissionRepository;
    private final ReactiveApiClient reactiveApiClient;
    private final ApplicationDocumentLogRepository applicationDocumentLogRepository;

    public LinkedDocumentResolver(ApplicationDocumentSubmissionRepository applicationDocumentSubmissionRepository, ReactiveApiClient reactiveApiClient, ApplicationDocumentLogRepository applicationDocumentLogRepository) {
        this.applicationDocumentSubmissionRepository = applicationDocumentSubmissionRepository;
        this.reactiveApiClient = reactiveApiClient;
        this.applicationDocumentLogRepository = applicationDocumentLogRepository;
    }

    public Mono<List<ResolvedDocument>> resolve(UserSessionObject user,
                                                ApplicationFlowStatusEntity flow,
                                                ServiceMeta service,
                                                DocumentGenerationDetails.DocMappingDTO mapping,
                                                String txnId) {

        String linkedTaskId = mapping.getLinkDocumentFromTask().getValue();
        String linkedReferenceId = mapping.getLinkedDocument().getValue();

        return applicationDocumentSubmissionRepository
                .findFirstByApplicationIdAndTaskIdAndReferenceIdAndStatusOrderByCreatedOnDesc(
                        flow.getApplicationId(),
                        linkedTaskId,
                        linkedReferenceId,
                        "P")
                .switchIfEmpty(Mono.error(
                        new SPRuntimeError("Linked document not found.", HttpStatus.BAD_REQUEST, flow.getTxnId()))
                )
                .flatMap(entity -> buildResolvedDocument(entity, mapping,user, flow.getTxnId()));
    }

    private Mono<List<ResolvedDocument>> buildResolvedDocument(
            ApplicationDocumentSubmissionEntity entity,
            DocumentGenerationDetails.DocMappingDTO mapping,
            UserSessionObject user,
            String txnId) {

        ResolvedDocument document = new ResolvedDocument();

        document.setUploadId(entity.getUploadId());
        document.setDocumentId(entity.getId());
        document.setReferenceId(document.getReferenceId());
        document.setDocumentName(entity.getDocumentName());
        document.setSourceType(LINKED_DOC);
        document.setStatus(entity.getStatus());
        document.setUploadId(entity.getUploadId());

        return reactiveApiClient
                .getFileView(user, List.of(entity.getUploadId()), txnId)
                .flatMap(response -> {

                    FileViewResponse.Data data = response.getResults().get(entity.getUploadId());

                    if (data != null && data.getData() != null) {

                        data.getData().forEach(item -> {

                            switch (item.getType()) {

                                case "preview" ->
                                        document.setPreviewUrl(item.getData().getUrl());

                                case "download" ->
                                        document.setDownloadUrl(item.getData().getUrl());

                                case "thumbnail" ->
                                        document.setThumbnailUrl(item.getData().getUrl());
                            }
                        });
                    }

                    if ("true".equalsIgnoreCase(mapping.getDigitalSignatureRequired())
                            || "optional".equalsIgnoreCase(mapping.getDigitalSignatureRequired())) {

                        return reactiveApiClient
                                .downloadFromPresignedUrl(document.getPreviewUrl())
                                .map(bytes -> {

                                    document.setBase64Content(
                                            Base64.getEncoder().encodeToString(bytes));

                                    document.setDigitalSignatureModes(List.of());

                                    return List.of(document);
                                });
                    }

                    document.setDigitalSignatureModes(List.of());

                    return Mono.just(List.of(document));
                });
    }

    public Mono<List<ResolvedDocument>> resolveSubmission(
            UserSessionObject user,
            ApplicationFlowStatusEntity flow,
            ServiceMeta service,
            DocumentGenerationDetails.DocMappingDTO mapping,
            DocumentGenerationRequest.DocumentSectionRequest.DocumentRequest document,
            String txnId) {

        return applicationDocumentSubmissionRepository
                .findById(document.getDocumentId())
                .switchIfEmpty(Mono.error(
                        new SPRuntimeError(
                                "Linked document not found.",
                                HttpStatus.BAD_REQUEST,
                                flow.getTxnId())))
                .flatMap(entity -> {

                    if (!flow.getApplicationId().equals(entity.getApplicationId())
                            || !mapping.getLinkDocumentFromTask().getValue().equals(entity.getTaskId())
                            || !mapping.getLinkedDocument().getValue().equals(entity.getReferenceId())) {

                        return Mono.error(new SPRuntimeError("Invalid linked document.", HttpStatus.BAD_REQUEST, flow.getTxnId()));
                    }

                    ApplicationDocumentLogEntity log = new ApplicationDocumentLogEntity();

                    String processId = isNull(flow.getCurrentProcess()) ? null : flow.getCurrentProcess().getProcessId();

                    log.setNewEntity(Boolean.TRUE);
                    log.setId(createUniqueId());
                    log.setTxnId(flow.getTxnId());
                    log.setApplicationId(flow.getApplicationId());
                    log.setServiceId(service.getServiceId());
                    log.setTaskId(service.getTaskId());
                    log.setProcessId(processId);

                    log.setReferenceId(document.getReferenceId());
                    log.setDocumentName(entity.getDocumentName());

                    log.setSourceType(LINKED_DOC);
                    log.setUploadId(entity.getUploadId());
                    log.setStatus("COMPLETED");
                    log.setCreatedOn(LocalDateTime.now());
                    log.setTenantId(user.getTenantId());

                    return applicationDocumentLogRepository
                            .save(log)
                            .flatMap(savedLog ->
                                    buildResolvedDocument(entity, mapping, user, txnId)
                                            .map(documents -> {
                                                documents.getFirst().setDocumentId(savedLog.getId());
                                                return documents;
                                            }));
                });
    }



}