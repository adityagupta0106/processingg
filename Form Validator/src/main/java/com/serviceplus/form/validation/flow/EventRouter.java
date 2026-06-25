package com.serviceplus.form.validation.flow;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.handlers.ApplicationFlowHandler;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;
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

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_STATUS_KEY;
import static com.serviceplus.form.validation.utility.HandlerMapping.HANDLERS;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static com.serviceplus.form.validation.utility.Utility.isEmpty;
import static java.util.Objects.isNull;

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

    @Autowired
    private ProcessingTxnRepository processingTxnRepository;

    public Mono<ServerResponse> route(String statusKey, String applicationId, ServerRequest request, String txnId,
                                      ServiceMeta services, boolean fromDraft) {

        Mono<ApplicationFlowStatusEntity> flow = Mono.empty();

        applicationFlowLogs.info("Checking route for applicationId {} txnId {} ,statusKey{}",applicationId,txnId,statusKey);
        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());

        if(fromDraft && isEmpty(txnId)){
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
                        generate(ACTIVITY_FORM_STATUS_KEY, applicationId, request, txnId, Mono.just(data),
                                new ApplicationFlowStatusEntity(),services,true,fromDraft,data.getUserId())
                )
                .switchIfEmpty(
                        flow.switchIfEmpty(Mono.error(new SPRuntimeError("Invalid Form Request [H - 01]", HttpStatus.BAD_REQUEST,txnId)))
                            .flatMap(fl ->
                                generate(fl.getActivityType(), applicationId, request, txnId, Mono.empty(), fl,services,false,fromDraft,0l)
                             )
                );


    }

    public Mono<ServerResponse> generate(String statusKey, String applicationId, ServerRequest request, String txnId, Mono<TempTransactionLogs> fetch,
                                         ApplicationFlowStatusEntity flow, ServiceMeta service, boolean cache, boolean fromDraft, Long userId) {
        ApplicationFlowHandler handler = HANDLERS.get(statusKey);

        if (handler != null) {
            applicationFlowLogs.info("handler processed for applicationId {} txnId {} cache {} draft {} ,statusKey{},class {}",
                                                applicationId, txnId,cache,fromDraft, statusKey, handler.getClass());

                if(cache){
                    return next(statusKey, applicationId, request, fetch, flow, service, txnId, fromDraft, userId);
                }
                else if (fromDraft) {
                    return transactionGeneration.createNewTransactionAndUpdateInFlow(service, flow, request.exchange().getRequest())
                            .flatMap(processingTxn -> next(statusKey, applicationId, request, fetch, flow, service, processingTxn.getTxnId(), true, processingTxn.getUserId()));
                } else {
                    return processingTxnRepository.findById(txnId)
                            .switchIfEmpty(
                                    Mono.error(new SPRuntimeError("Invalid txnId",HttpStatus.BAD_REQUEST,txnId))
                            )
                            .flatMap(
                                    _txn -> next(statusKey, applicationId, request, fetch, flow, service, txnId, false, _txn.getUserId())
                            );
                }
        } else {
            throw new IllegalArgumentException("No handler found for status: ".concat(statusKey));
        }
    }

    public Mono<ServerResponse> next(String statusKey, String applicationId, ServerRequest request, Mono<TempTransactionLogs> fetch,
                                     ApplicationFlowStatusEntity flow, ServiceMeta service, String txnId, boolean fromDraft, Long userId) {

        ApplicationFlowHandler handler = HANDLERS.get(statusKey);

        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());

        if(isNull(user) || !userId.equals(user.getUserID())){
            return Mono.error(new SPRuntimeError("Unauthorized application access",HttpStatus.FORBIDDEN,txnId));
        }

        if(fromDraft){
            return handler.fetch(applicationId, request, statusKey, txnId, fetch, flow,service, true);
        }
        else{
            return handler.process(applicationId, request, statusKey, txnId, fetch, flow,service, false);
        }
    }

}
