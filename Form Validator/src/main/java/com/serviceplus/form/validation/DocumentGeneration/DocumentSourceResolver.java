package com.serviceplus.form.validation.DocumentGeneration;

import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentSourceResolver {

    private final LinkedDocumentResolver linkedResolver;
    private final UploadedDocumentResolver uploadResolver;
    private final SystemGeneratedDocumentResolver systemResolver;

    public DocumentSourceResolver(LinkedDocumentResolver linkedResolver, UploadedDocumentResolver uploadResolver, SystemGeneratedDocumentResolver systemResolver) {
        this.linkedResolver = linkedResolver;
        this.uploadResolver = uploadResolver;
        this.systemResolver = systemResolver;
    }

    public Mono<List<ResolvedDocument>> resolve(UserSessionObject user,
                                                ApplicationFlowStatusEntity flow,
                                                ServiceMeta service,
                                                DocumentGenerationDetails.DocMappingDTO mapping,
                                                String txnId) {

        List<Mono<List<ResolvedDocument>>> sources = new ArrayList<>();

        List<String> documentSources = mapping.getDocumentSource();

        if (documentSources != null) {

            if (documentSources.contains("linked")) {
                sources.add(linkedResolver.resolve(
                        user,
                        flow,
                        service,
                        mapping,
                        txnId));
            }

            if (documentSources.contains("systemGenerated")) {
                sources.add(systemResolver.resolve(
                        user,
                        flow,
                        service,
                        mapping,
                        txnId));
            }

            if (documentSources.contains("fileUpload")) {
                sources.add(uploadResolver.resolve(
                        user,
                        flow,
                        service,
                        mapping,
                        txnId));
            }
        }

        if (sources.isEmpty()) {
            return Mono.just(List.of());
        }

        return Flux.mergeSequential(sources)
                .flatMapIterable(list -> list)
                .collectList();
    }

    public Mono<List<ResolvedDocument>> resolveSubmission(UserSessionObject user,
                                                          ApplicationFlowStatusEntity flow,
                                                          ServiceMeta service,
                                                          DocumentGenerationDetails.DocMappingDTO mapping,
                                                          DocumentGenerationRequest.DocumentSectionRequest section,
                                                          String txnId) {

        List<Mono<List<ResolvedDocument>>> sources = new ArrayList<>();

        if (section.getDocuments() != null) {

            section.getDocuments().forEach(document -> {

                switch (document.getSourceType()) {

                    case "linked":
                        sources.add(linkedResolver.resolveSubmission(
                                user,
                                flow,
                                service,
                                mapping,
                                document,
                                txnId));
                        break;

                    case "systemGenerated":
                        sources.add(systemResolver.resolveSubmission(
                                user,
                                flow,
                                service,
                                mapping,
                                document,
                                txnId));
                        break;

                    case "fileUpload":
                        sources.add(uploadResolver.resolveSubmission(
                                user,
                                flow,
                                service,
                                mapping,
                                document,
                                txnId));
                        break;

                    default:
                        break;
                }
            });
        }

        if (sources.isEmpty()) {
            return Mono.just(List.of());
        }

        return Flux.mergeSequential(sources)
                .flatMapIterable(list -> list)
                .collectList();
    }

}
