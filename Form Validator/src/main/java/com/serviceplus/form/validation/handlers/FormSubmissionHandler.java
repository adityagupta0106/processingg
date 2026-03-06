package com.serviceplus.form.validation.handlers;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;
import com.serviceplus.form.validation.service.FormService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static com.serviceplus.form.validation.utility.Utility.isEmpty;

@Service
@SanitizeRequest
public class FormSubmissionHandler implements ApplicationFlowHandler {

    @Autowired
    private FormService formService;

    @Autowired
    private ProcessingTxnRepository txnRepository;

    @Override
    public Mono<ServerResponse> process(String applicationId, ServerRequest request, String statusKey, String txnId, Mono<TempTransactionLogs> tempLog
                                                                , ApplicationFlowStatusEntity flowStatus, ServiceMeta service, boolean fromDraft) {

        Optional<String> applyKeyOpt = request.queryParam("serviceKey");
        Optional<String> appIdOpt = request.queryParam("appId");
        Optional<String> serviceIdOpt = request.queryParam("serviceId");

        if (isEmpty(txnId) || applyKeyOpt.isEmpty() || serviceIdOpt.isEmpty()) {
            return Mono.error(new SPRuntimeError("Parameters missing", HttpStatus.BAD_REQUEST,txnId));
        }

        String applId = appIdOpt.orElse("");

        Mono<String> bodyMono = request.bodyToMono(String.class).cache();

        return bodyMono.hasElement()
                .flatMap(hasBody -> {
                    if (hasBody) {
                        return bodyMono.flatMap(appData ->
                                formService.applicationSubmission(
                                        request.exchange().getRequest(), txnId, appData,
                                        applId, fromDraft, request, flowStatus,
                                        serviceIdOpt.get(), service
                                )
                        );
                    } else {
                        return fetch(applicationId, request, statusKey, txnId,
                                tempLog, flowStatus, service, fromDraft);
                    }
                });

    }

    @Override
    public Mono<ServerResponse> fetch(String applicationId, ServerRequest request, String statusKey, String txnId, Mono<TempTransactionLogs> fetch,
                                      ApplicationFlowStatusEntity flow,ServiceMeta service, boolean fromDraft) {
        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
        Optional<String> serviceIdOpt = request.queryParam("serviceId");

        if(serviceIdOpt.isEmpty()){
            return Mono.error(new SPRuntimeError("Key missing",HttpStatus.BAD_REQUEST,txnId));
        }

        return formService.fetchFormData(flow.getDataId(),flow.getFormId(),user, flow.getTxnId(), applicationId,serviceIdOpt.get(),service);
    }
}
