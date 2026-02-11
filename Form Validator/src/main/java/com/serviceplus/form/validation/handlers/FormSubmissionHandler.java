package com.serviceplus.form.validation.handlers;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;
import com.serviceplus.form.validation.service.FormSubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.serviceplus.form.validation.utility.Utility.isEmpty;

@Service
@SanitizeRequest
public class FormSubmissionHandler implements ApplicationFlowHandler {

    @Autowired
    private FormSubmissionService formSubmissionService;

    @Autowired
    private ProcessingTxnRepository txnRepository;

    @Override
    public Mono<ServerResponse> process(String applicationId, ServerRequest request, String statusKey, String txnId, Mono<TempTransactionLogs> tempLog
                                                                , ApplicationFlowStatusEntity flowStatus, boolean fromDraft) {

        //CHECK IF REQUEST IS COMING FROM DRAFT
        boolean draft = false;
        Optional<String> applyKeyOpt = request.queryParam("serviceKey");
        Optional<String> appIdOpt = request.queryParam("appId");
        Optional<String> serviceIdOpt = request.queryParam("serviceId");

        if (isEmpty(txnId) || applyKeyOpt.isEmpty() || serviceIdOpt.isEmpty()) {
            return Mono.error(new SPRuntimeError("Parameters missing", HttpStatus.BAD_REQUEST));
        }

        String applyKey = applyKeyOpt.get();
        String applId = appIdOpt.orElse("");

        return request.bodyToMono(String.class)
                .switchIfEmpty(
                        Mono.error(new SPRuntimeError("Form data not found in request",HttpStatus.BAD_REQUEST))
                )
                .flatMap(appData ->
                        formSubmissionService.applicationSubmission(
                                request.exchange().getRequest(), txnId, appData, applyKey,applId,draft,request,flowStatus,serviceIdOpt.get()
                        )
                );
    }
}
