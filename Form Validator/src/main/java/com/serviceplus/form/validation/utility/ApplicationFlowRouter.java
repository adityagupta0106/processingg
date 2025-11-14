package com.serviceplus.form.validation.utility;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.handlers.ApplicationFlowHandler;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.service.TempTransactionLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.HandlerMapping.HANDLERS;
import static com.serviceplus.form.validation.utility.Utility.isEmpty;

@Service
@SanitizeRequest
public class ApplicationFlowRouter {

    @Autowired
    private ApplicationFlowRouterRepository applicationFlowRouterRepository;

    @Autowired
    private TempTransactionLogService tempTransactionLogService;

    public Mono<ServerResponse> route(String statusKey, String applicationId, ServerRequest request, String txnId) {

        Mono<ApplicationFlowStatusEntity> flow = Mono.empty();

        if(isEmpty(statusKey)){
            flow = applicationFlowRouterRepository.findByApplicationIdAndProcessIdAndCompleted(
                    applicationId == null ? "-1" : applicationId,txnId,false
            );

            //DELETE BELOW RECORDS FOR SAME PROCESS ID (COMING FROM EDIT)
        }
        else{
            flow = applicationFlowRouterRepository.findByApplicationIdAndProcessIdAndStatusAndCompleted(
                    applicationId == null ? "-1" : applicationId,txnId,statusKey,false
            );
        }

        return tempTransactionLogService.fetch(txnId)
                .flatMap(data ->
                        generate("FS", applicationId, request, txnId, Mono.just(data), new ApplicationFlowStatusEntity())
                )
                .switchIfEmpty(
                        flow.switchIfEmpty(Mono.error(new SPRuntimeError("Invalid Form Request [H - 01]", HttpStatus.BAD_REQUEST)))
                            .flatMap(fl ->
                                generate(fl.getStatus(), applicationId, request, txnId, Mono.empty(), fl)
                             )
                );


    }

    public Mono<ServerResponse> generate(String statusKey, String applicationId, ServerRequest request, String txnId, Mono<TempTransactionLogs> fetch,
                                         ApplicationFlowStatusEntity flow){
        ApplicationFlowHandler handler = HANDLERS.get(statusKey);
        if (handler != null) {
            return handler.process(applicationId,request,statusKey,txnId,fetch,flow);
        } else {
            throw new IllegalArgumentException("No handler found for status: ".concat(statusKey));
        }
    }

}
