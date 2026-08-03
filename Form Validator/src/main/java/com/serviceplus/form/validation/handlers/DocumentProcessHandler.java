package com.serviceplus.form.validation.handlers;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.DocumentGeneration.DocumentProcessService;
import com.serviceplus.form.validation.dto.DocumentGenerationRequest;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.enums.DocumentMode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_DOCUMENT_GENERATION;
import static java.util.Objects.isNull;

@Service
@SanitizeRequest
public class DocumentProcessHandler implements ApplicationFlowHandler {

    private final DocumentProcessService documentProcessService;

    public DocumentProcessHandler(DocumentProcessService documentProcessService) {
        this.documentProcessService = documentProcessService;
    }

    @Override
    public String getActivityType() {
        return ACTIVITY_DOCUMENT_GENERATION;
    }

    @Override
    public Mono<ServerResponse> process(String applicationId,
                                        ServerRequest request,
                                        String statusKey,
                                        String txnId,
                                        Mono<TempTransactionLogs> fetch,
                                        ApplicationFlowStatusEntity flow,
                                        ServiceMeta service,
                                        boolean fromDraft) {

        return request.bodyToMono(DocumentGenerationRequest.class)
                .defaultIfEmpty(new DocumentGenerationRequest())
                .flatMap(body -> {

                    DocumentMode mode = (body.getDocumentSections() == null || body.getDocumentSections().isEmpty())
                                                ? DocumentMode.FETCH : DocumentMode.SUBMIT;

                    return documentProcessService.process(
                            applicationId,
                            request,
                            statusKey,
                            txnId,
                            fetch,
                            flow,
                            service,
                            fromDraft,
                            body,
                            mode
                    );
                })
                .switchIfEmpty(
                        fetch(applicationId, request, statusKey, txnId, fetch, flow, service, fromDraft)
                );
    }

    @Override
    public Mono<ServerResponse> fetch(String applicationId,
                                      ServerRequest request,
                                      String statusKey,
                                      String txnId,
                                      Mono<TempTransactionLogs> fetch,
                                      ApplicationFlowStatusEntity flow,
                                      ServiceMeta service,
                                      boolean fromDraft) {

        return documentProcessService.process(applicationId, request, statusKey, txnId, fetch, flow, service, fromDraft,null,DocumentMode.FETCH);
    }
}
