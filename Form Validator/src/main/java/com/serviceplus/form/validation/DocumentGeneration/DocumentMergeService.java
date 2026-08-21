package com.serviceplus.form.validation.DocumentGeneration;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.ApplicationDocumentMergeEntity;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.enums.DocumentMergeMode;
import com.serviceplus.form.validation.repository.ApplicationDocumentMergeRepository;
import com.serviceplus.form.validation.service.ReactiveApiClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static java.util.Objects.isNull;

@Service
public class DocumentMergeService {

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");
    private static final String MERGED_STATUS = "merged";

    private final ReactiveApiClient reactiveApiClient;
    private final ApplicationDocumentMergeRepository applicationDocumentMergeRepository;

    public DocumentMergeService(ReactiveApiClient reactiveApiClient, ApplicationDocumentMergeRepository applicationDocumentMergeRepository) {
        this.reactiveApiClient = reactiveApiClient;
        this.applicationDocumentMergeRepository = applicationDocumentMergeRepository;
    }

    public Mono<byte[]> fetchDocumentBytes(UserSessionObject user, ResolvedDocument document, String txnId) {

        if (document == null || document.getDownloadUrl() == null) {
            return Mono.error(new SPRuntimeError("Document download URL not available.", HttpStatus.BAD_REQUEST, txnId));
        }

        return reactiveApiClient
                .downloadFromPresignedUrl(document.getDownloadUrl())
                .doOnNext(bytes ->
                        applicationFlowLogs.info("TxnId : {} | Downloaded document {} | Size: {} bytes", txnId, document.getDocumentName(), bytes.length)
                )
                .switchIfEmpty(
                        Mono.error(new SPRuntimeError("Unable to download document.", HttpStatus.BAD_REQUEST, txnId))
                );
    }

    public Mono<ResolvedDocument> process(DocumentMergeResult mergeResult, UserSessionObject user, String txnId, ApplicationFlowStatusEntity flow) {

        if (mergeResult == null || mergeResult.documents() == null || mergeResult.documents().isEmpty()) {
            return Mono.error(new SPRuntimeError("No documents available for processing.", HttpStatus.BAD_REQUEST, txnId));
        }

        if (mergeResult.mode() == DocumentMergeMode.NONE) {
            return Mono.just(mergeResult.documents().getFirst());
        }

        if (mergeResult.mode() == DocumentMergeMode.OVERWRITE) {
            return overwrite(mergeResult.documents().getFirst(), user, txnId,flow);
        }

        if (mergeResult.mode() == DocumentMergeMode.MERGE_ALL) {
            return merge(mergeResult.documents(), user, txnId,flow);
        }

        return Mono.error(new SPRuntimeError("Invalid document merge mode.", HttpStatus.BAD_REQUEST, txnId));
    }

    private Mono<ResolvedDocument> merge(List<ResolvedDocument> documents, UserSessionObject user, String txnId, ApplicationFlowStatusEntity flow) {

        applicationFlowLogs.info("TxnId : {} | Starting document merge. Total documents : {}", txnId,
                documents.size());


        return Flux.fromIterable(documents)
                .concatMap(document ->
                        fetchDocumentBytes(
                                user,
                                document,
                                txnId))

                .collectList()

                .flatMap(documentBytes -> {

                    if (documentBytes.isEmpty()) {
                        return Mono.error(new SPRuntimeError("No document content available for merge.", HttpStatus.BAD_REQUEST, txnId));
                    }

                    return mergePdfDocuments(documentBytes, txnId);
                })

                .flatMap(mergedBytes ->
                        uploadMergedDocument(mergedBytes, documents.getFirst(), user, txnId,flow)
                );
    }

    private Mono<byte[]> mergePdfDocuments(List<byte[]> documents, String txnId) {

        return Mono.fromCallable(() -> {

            PDFMergerUtility merger = new PDFMergerUtility();

            for (byte[] bytes : documents) {

                if (bytes == null || bytes.length == 0) {
                    continue;
                }

                merger.addSource(new RandomAccessReadBuffer(bytes));
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();

            merger.setDestinationStream(output);
            merger.mergeDocuments(null);

            byte[] mergedBytes = output.toByteArray();

            applicationFlowLogs.info("TxnId : {} | Successfully merged {} documents | Final size : {} bytes", txnId, documents.size(), mergedBytes.length);

            return mergedBytes;

        }).onErrorMap(ex -> {

            applicationFlowLogs.error("TxnId : {} | Error while merging PDF documents", txnId, ex);

            return new SPRuntimeError("Unable to merge documents.", HttpStatus.INTERNAL_SERVER_ERROR, txnId);
        });
    }


    private Mono<ResolvedDocument> uploadMergedDocument(
            byte[] mergedBytes,
            ResolvedDocument firstDocument,
            UserSessionObject user,
            String txnId, ApplicationFlowStatusEntity flow) {

        String documentName = firstDocument.getDocumentName();

        if (documentName == null || documentName.isBlank()) {
            documentName = "merged-document.pdf";
        }

        if (!documentName.toLowerCase().endsWith(".pdf")) {
            documentName = documentName + ".pdf";
        }

        final String finalDocumentName = documentName;

        applicationFlowLogs.info("TxnId : {} | Creating upload session for merged document : {} | Size : {} bytes", txnId, finalDocumentName, mergedBytes.length);

        CreateUploadSessionsRequest request = new CreateUploadSessionsRequest();
        request.setSourceService("processing");
        request.setUserId(user.getUserID());

        CreateUploadSessionsRequest.FileUploadRequest file = new CreateUploadSessionsRequest.FileUploadRequest();

        file.setReferenceId(firstDocument.getReferenceId());
        file.setCategory(firstDocument.getDocumentName());

        file.setAllowedMime(List.of(MediaType.APPLICATION_PDF_VALUE));

        file.setMaxFileSize(52428800L);
        file.setChunkedUpload(Boolean.TRUE);
        file.setExpiresInMinutes(30);
        file.setFunctionality("document-generation");

        request.setFiles(List.of(file));

        return reactiveApiClient
                .createUploadSession(
                        user,
                        request,
                        txnId)
                .flatMap(sessionResponse -> {

                    if (sessionResponse == null || sessionResponse.getUploads() == null || sessionResponse.getUploads().isEmpty()) {
                        return Mono.error(new SPRuntimeError("Unable to create upload session for merged document.", HttpStatus.BAD_REQUEST, txnId));
                    }

                    CreateUploadSessionsResponse.UploadSessionItem session = sessionResponse.getUploads().getFirst();

                    String uploadId = session.getUploadId();
                    String uploadToken = session.getUploadToken();

                    applicationFlowLogs.info("TxnId : {} | Upload session created | UploadId : {} | ReferenceId : {}", txnId, uploadId, session.getReferenceId());

                    return reactiveApiClient
                            .uploadBase64File(
                                    user,
                                    uploadId,
                                    uploadToken,
                                    mergedBytes,
                                    finalDocumentName,
                                    txnId)
                            .flatMap(uploadResponse -> {


                                ApplicationDocumentMergeEntity entity = new ApplicationDocumentMergeEntity();
                                String processId = isNull(flow.getCurrentProcess()) ? null : flow.getCurrentProcess().getProcessId();

                                entity.setNew(Boolean.TRUE);
                                entity.setId(createUniqueId());
                                entity.setApplicationId(flow.getApplicationId());
                                entity.setTxnId(txnId);
                                entity.setProcessId(processId);
                                entity.setTaskId(flow.getTaskId());
                                entity.setReferenceId(firstDocument.getReferenceId());
                                entity.setMergedUploadId(uploadId);
                                entity.setStatus("P");
                                entity.setCreatedBy(user.getUserID().intValue());
                                entity.setCreatedOn(LocalDateTime.now());

                                entity.setTenantId(user.getTenantId());

                                return applicationDocumentMergeRepository
                                        .save(entity)
                                        .map(saved -> {

                                            ResolvedDocument result = new ResolvedDocument();

                                            result.setUploadId(uploadId);
                                            result.setDocumentId(saved.getId());
                                            result.setDocumentName(finalDocumentName);
                                            result.setReferenceId(firstDocument.getReferenceId());
                                            result.setSourceType(MERGED_STATUS);
                                            result.setPreviewUrl(uploadResponse.getPreviewUrl());

                                            applicationFlowLogs.info("TxnId : {} | Merge entity saved. MergeId : {} | UploadId : {}", txnId, saved.getId(), uploadId);

                                            return result;
                                        });
                            });
                });
    }


    private Mono<ResolvedDocument> overwrite(ResolvedDocument document, UserSessionObject user, String txnId, ApplicationFlowStatusEntity flow) {

        applicationFlowLogs.info("TxnId : {} | OVERWRITE mode | Selected document : {} | UploadId : {}", txnId, document.getDocumentName(), document.getUploadId());

        if (document.getUploadId() == null || document.getUploadId().isBlank()) {
            return Mono.error(new SPRuntimeError("Unable to overwrite document. UploadId is missing.", HttpStatus.BAD_REQUEST, txnId));
        }

        String processId = isNull(flow.getCurrentProcess()) ? null : flow.getCurrentProcess().getProcessId();

        ApplicationDocumentMergeEntity mergeEntity = new ApplicationDocumentMergeEntity();

        mergeEntity.setId(createUniqueId());

        mergeEntity.setTxnId(txnId);
        mergeEntity.setApplicationId(flow.getApplicationId());
        mergeEntity.setProcessId(processId);
        mergeEntity.setTaskId(flow.getTaskId());

        mergeEntity.setReferenceId(document.getReferenceId());
        mergeEntity.setMergedUploadId(document.getUploadId());
        mergeEntity.setStatus("P");
        mergeEntity.setCreatedBy(user.getUserID().intValue());
        mergeEntity.setCreatedOn(LocalDateTime.now());

        mergeEntity.setTenantId(user.getTenantId());
        mergeEntity.setNew(Boolean.TRUE);

        return applicationDocumentMergeRepository
                .save(mergeEntity)
                .doOnNext(saved ->
                        applicationFlowLogs.info("TxnId : {} | OVERWRITE merge entity saved | " + "MergeId: {} | ReferenceId: {} | UploadId: {}", txnId, saved.getId(), saved.getReferenceId(), saved.getMergedUploadId())
                )
                .thenReturn(document);
    }
}