package com.serviceplus.form.validation.flow;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.Services;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.handlers.ApplicationFlowHandler;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.service.TempTransactionLogService;
import com.serviceplus.form.validation.service.TransactionGeneration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.HandlerMapping.HANDLERS;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

@Service
@SanitizeRequest
public class EventRouter {

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    @Autowired
    private ApplicationFlowRouterRepository applicationFlowRouterRepository;

    @Autowired
    private TempTransactionLogService tempTransactionLogService;

    @Autowired
    private TransactionGeneration transactionGeneration;

    public Mono<ServerResponse> route(String statusKey, String applicationId, ServerRequest request, String txnId, Services services, boolean fromDraft) {

        Mono<ApplicationFlowStatusEntity> flow = Mono.empty();

        applicationFlowLogs.info("Checking route for applicationId {} txnId {} ,statusKey{}",applicationId,txnId,statusKey);
        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());

        if(fromDraft){
            flow = applicationFlowRouterRepository.findByApplicationIdAndCompletedAndTaskIdAndServiceIdAndTenantId(
                    applicationId == null ? "-1" : applicationId,0,services.getTaskId(),services.getServiceId(),user.getTenantId()
            );
        }
        else{
            flow = applicationFlowRouterRepository.findByApplicationIdAndTxnIdAndCompletedAndTaskIdAndServiceIdAndTenantId(
                    applicationId == null ? "-1" : applicationId,txnId,0,services.getTaskId(),services.getServiceId(),user.getTenantId()
            );
        }

        return tempTransactionLogService.fetch(txnId)
                .flatMap(data ->
                        generate("FS", applicationId, request, txnId, Mono.just(data), new ApplicationFlowStatusEntity(),services,true,fromDraft)
                )
                .switchIfEmpty(
                        flow.switchIfEmpty(Mono.error(new SPRuntimeError("Invalid Form Request [H - 01]", HttpStatus.BAD_REQUEST)))
                            .flatMap(fl ->
                                generate(fl.getActivityType(), applicationId, request, txnId, Mono.empty(), fl,services,false,fromDraft)
                             )
                );


    }

    public Mono<ServerResponse> generate(String statusKey, String applicationId, ServerRequest request, String txnId, Mono<TempTransactionLogs> fetch,
                                         ApplicationFlowStatusEntity flow, Services service, boolean cache, boolean fromDraft) {
        ApplicationFlowHandler handler = HANDLERS.get(statusKey);

        if (handler != null) {
            applicationFlowLogs.info("handler processed for applicationId {} txnId {} ,statusKey{},class {}", applicationId, txnId, statusKey, handler.getClass());
            if (cache) {
                return handler.process(applicationId, request, statusKey, txnId, fetch, flow,fromDraft);

            } else {
                return transactionGeneration.createNewTransactionAndUpdateInFlow(service, flow, request.exchange().getRequest())
                        .flatMap(processingTxn -> handler.process(applicationId, request, statusKey, processingTxn.getTxnId(), fetch, flow,fromDraft));
            }
        } else {
            throw new IllegalArgumentException("No handler found for status: ".concat(statusKey));
        }
    }

}
