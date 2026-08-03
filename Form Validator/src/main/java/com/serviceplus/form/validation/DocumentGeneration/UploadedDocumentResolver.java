package com.serviceplus.form.validation.DocumentGeneration;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.ApplicationDocumentLogEntity;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.repository.ApplicationDocumentLogRepository;
import com.serviceplus.form.validation.service.ReactiveApiClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.serviceplus.form.validation.utility.Utility.MIME_TYPE_MAP;

@Service
public class UploadedDocumentResolver {

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    private static final String FILE_UPLOAD = "fileUpload";

    private final ApplicationDocumentLogRepository applicationDocumentRepository;

    private final ReactiveApiClient reactiveApiClient;

    public UploadedDocumentResolver(ApplicationDocumentLogRepository applicationDocumentRepository, ReactiveApiClient reactiveApiClient) {
        this.applicationDocumentRepository = applicationDocumentRepository;
        this.reactiveApiClient = reactiveApiClient;
    }

    public Mono<List<ResolvedDocument>> resolve(UserSessionObject user,
                                                ApplicationFlowStatusEntity flow,
                                                ServiceMeta service,
                                                DocumentGenerationDetails.DocMappingDTO mapping,
                                                String txnId) {

        if (mapping.getFileType() == null || mapping.getFileType().isEmpty()) {
            return Mono.error(new SPRuntimeError("No file type configured for uploaded document.", HttpStatus.INTERNAL_SERVER_ERROR, flow.getTxnId()
            ));
        }

        return applicationDocumentRepository
                .findFirstByApplicationIdAndTaskIdAndReferenceIdAndSourceTypeOrderByCreatedOnDesc(
                        flow.getApplicationId(),
                        flow.getTxnId(),
                        flow.getTaskId(),
                        mapping.getReferenceId(),
                        FILE_UPLOAD
                )
                .flatMap(entity -> buildResolvedDocument(entity, user, flow.getTxnId()))
                .switchIfEmpty(Mono.defer(() -> {

                    applicationFlowLogs.info(
                            "Uploaded document not found. ApplicationId: {}, ReferenceId: {}, txnId: {}",
                            flow.getApplicationId(),
                            mapping.getReferenceId(),
                            flow.getTxnId());

                    ResolvedDocument document = new ResolvedDocument();

                    document.setDocumentId(null);
                    document.setReferenceId(mapping.getReferenceId());
                    document.setDocumentName(mapping.getDocumentName());
                    document.setSourceType(FILE_UPLOAD);

                    document.setStatus("PENDING");

                    document.setPreviewUrl(null);
                    document.setDownloadUrl(null);
                    document.setThumbnailUrl(null);

                    document.setDigitalSignatureModes(List.of());
                    document.setFileTypes(mapping.getFileType());
                    document.setFileUploadOptional(mapping.getIsFileUploadOptional());

                    CreateUploadSessionsRequest uploadRequest = new CreateUploadSessionsRequest();

                    uploadRequest.setUserId(user.getUserID());
                    uploadRequest.setSourceService("FORM_VALIDATION");

                    CreateUploadSessionsRequest.FileUploadRequest file = new CreateUploadSessionsRequest.FileUploadRequest();

                    file.setReferenceId(mapping.getReferenceId());
                    file.setCategory(mapping.getDocumentName());

                    file.setAllowedMime(
                            mapping.getFileType()
                                    .stream()
                                    .map(DocumentGenerationDetails.DocMappingDTO.LabelValueDTO::getValue)
                                    .map(String::toLowerCase)
                                    .map(ext -> MIME_TYPE_MAP.getOrDefault(ext, ext))
                                    .toList()
                    );

                    file.setMaxFileSize(52428800L);

                    file.setChunkedUpload(Boolean.TRUE);
                    file.setExpiresInMinutes(30);

                    uploadRequest.setFiles(List.of(file));

                    return reactiveApiClient
                            .createUploadSession(user, uploadRequest, txnId)
                            .map(session -> {

                                document.setFileTypes(mapping.getFileType());
                                document.setFileUploadOptional(mapping.getIsFileUploadOptional());

                                document.setUploadSession(session.getUploads().getFirst());

                                return List.of(document);
                            });
                }));
    }

    private Mono<List<ResolvedDocument>> buildResolvedDocument(
            ApplicationDocumentLogEntity entity,
            UserSessionObject user,
            String txnId) {

        ResolvedDocument document = new ResolvedDocument();

        document.setDocumentId(entity.getId());
        document.setReferenceId(entity.getReferenceId());
        document.setDocumentName(entity.getDocumentName());
        document.setSourceType(FILE_UPLOAD);
        document.setStatus(entity.getStatus());

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

        if (document.getDocumentId() == null || document.getDocumentId().isBlank()) {

            if (Boolean.FALSE.equals(mapping.getIsFileUploadOptional())) {
                return Mono.error(new SPRuntimeError("Please upload '" + mapping.getDocumentName() + "'", HttpStatus.BAD_REQUEST, flow.getTxnId()));
            }

            return Mono.just(List.of());
        }

        ResolvedDocument resolved = new ResolvedDocument();

        resolved.setDocumentId(null);
        resolved.setReferenceId(mapping.getReferenceId());
        resolved.setDocumentName(mapping.getDocumentName());
        resolved.setSourceType(FILE_UPLOAD);
        resolved.setUploadId(document.getDocumentId());
        resolved.setStatus("COMPLETED");

        return Mono.just(List.of(resolved));
    }
}