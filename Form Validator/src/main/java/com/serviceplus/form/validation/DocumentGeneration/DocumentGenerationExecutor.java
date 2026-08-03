package com.serviceplus.form.validation.DocumentGeneration;

import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentGenerationExecutor {

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    private final DocumentSourceResolver sourceResolver;

    public DocumentGenerationExecutor(DocumentSourceResolver sourceResolver) {
        this.sourceResolver = sourceResolver;
    }

    public Mono<List<DocumentSectionResponse>> execute(String applicationId,
                                                       String txnId,
                                                       UserSessionObject user,
                                                       ApplicationFlowStatusEntity flow,
                                                       ServiceMeta service,
                                                       List<DocumentGenerationDetails.DocMappingDTO> mappings, boolean fetch) {

        applicationFlowLogs.info("TxnId : {} | Starting Document Generation | Total Mappings : {}", txnId, mappings.size());

        return Flux.fromIterable(mappings)
                .concatMap(mapping ->
                        processMapping(
                                applicationId,
                                txnId,
                                user,
                                flow,
                                service,
                                mapping))
                .collectList()
                .doOnSuccess(result ->
                        applicationFlowLogs.info("TxnId : {} | Document Generation Completed. Total Sections : {}", txnId, result.size())
                );
    }

    private Mono<DocumentSectionResponse> processMapping(String applicationId,
                                                         String txnId,
                                                         UserSessionObject user,
                                                         ApplicationFlowStatusEntity flow,
                                                         ServiceMeta service,
                                                         DocumentGenerationDetails.DocMappingDTO mapping) {

        applicationFlowLogs.info("TxnId : {} | Processing Mapping : {}", txnId, mapping.getDocumentName());

        return sourceResolver
                .resolve(
                        user,
                        flow,
                        service,
                        mapping,
                        txnId)
                .map(resolvedDocuments -> {

                    applicationFlowLogs.info("TxnId : {} | Resolved {} document(s) for mapping : {}", txnId, resolvedDocuments.size(), mapping.getDocumentName());

                    DocumentSectionResponse response = new DocumentSectionResponse();

                    response.setReferenceId(mapping.getReferenceId());
                    response.setDocumentName(mapping.getDocumentName());
                    response.setMergeRequired("mergeAll".equalsIgnoreCase(mapping.getMergeMode()));

                    response.setDocuments(resolvedDocuments);
                    response.setMergedDocument(null);

                    return response;
                });
    }

    public Mono<List<DocumentSectionResponse>> submit(String applicationId,
                                                      String txnId,
                                                      UserSessionObject user,
                                                      ApplicationFlowStatusEntity flow,
                                                      ServiceMeta service,
                                                      List<DocumentGenerationDetails.DocMappingDTO> mappings,
                                                      DocumentGenerationRequest request,
                                                      boolean fetch) {

        applicationFlowLogs.info("TxnId : {} | Starting Document Submission | Total Mappings : {}", txnId, mappings.size());

        return Flux.fromIterable(request.getDocumentSections())
                .concatMap(section -> {

                    DocumentGenerationDetails.DocMappingDTO mapping = mappings.stream()
                            .filter(m -> section.getDocuments()
                                    .stream()
                                    .anyMatch(d -> d.getReferenceId().equals(m.getReferenceId())))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "No mapping found for submitted documents."));

                    return processSubmission(
                            applicationId,
                            txnId,
                            user,
                            flow,
                            service,
                            mapping,
                            section);
                })
                .collectList()
                .doOnSuccess(result ->
                        applicationFlowLogs.info(
                                "TxnId : {} | Document Submission Completed. Total Sections : {}",
                                txnId,
                                result.size()));
    }

    private Mono<DocumentSectionResponse> processSubmission(String applicationId,
                                                            String txnId,
                                                            UserSessionObject user,
                                                            ApplicationFlowStatusEntity flow,
                                                            ServiceMeta service,
                                                            DocumentGenerationDetails.DocMappingDTO mapping,
                                                            DocumentGenerationRequest.DocumentSectionRequest section) {

        applicationFlowLogs.info(
                "TxnId : {} | Processing Submitted Mapping : {}",
                txnId,
                mapping.getDocumentName());

        return sourceResolver
                .resolveSubmission(
                        user,
                        flow,
                        service,
                        mapping,
                        section,
                        txnId)
                .map(resolvedDocuments -> {

                    applicationFlowLogs.info("TxnId : {} | Resolved {} submitted document for mapping : {}", txnId, resolvedDocuments.size(), mapping.getDocumentName());

                    DocumentSectionResponse response = new DocumentSectionResponse();

                    response.setReferenceId(mapping.getReferenceId());
                    response.setDocumentName(mapping.getDocumentName());
                    response.setMergeRequired("mergeAll".equalsIgnoreCase(mapping.getMergeMode()));
                    response.setDocuments(resolvedDocuments);
                    response.setMergedDocument(null);

                    return response;
                });
    }

}
