package com.serviceplus.form.validation.flow;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ActivityMapDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.handlers.ApplicationFlowHandler;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;
import com.serviceplus.form.validation.service.ActivityMapService;
import com.serviceplus.form.validation.service.TempTransactionLogService;
import com.serviceplus.form.validation.service.TransactionGeneration;
import com.serviceplus.form.validation.utility.HandlerMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Comparator;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_STATUS_KEY;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static com.serviceplus.form.validation.utility.Utility.isEmpty;
import static java.util.Objects.isNull;

@Service
@SanitizeRequest
public class EventRouter {

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    private final ApplicationFlowRouterRepository applicationFlowRouterRepository;

    private final TempTransactionLogService tempTransactionLogService;

    private final TransactionGeneration transactionGeneration;

    private final ProcessingTxnRepository processingTxnRepository;

    private final CurrentProcessRepository currentProcessRepository;

    private final ActivityMapService activityMapService;

    public EventRouter(ApplicationFlowRouterRepository applicationFlowRouterRepository, TempTransactionLogService tempTransactionLogService, TransactionGeneration transactionGeneration, ProcessingTxnRepository processingTxnRepository, CurrentProcessRepository currentProcessRepository, ActivityMapService activityMapService) {
        this.applicationFlowRouterRepository = applicationFlowRouterRepository;
        this.tempTransactionLogService = tempTransactionLogService;
        this.transactionGeneration = transactionGeneration;
        this.processingTxnRepository = processingTxnRepository;
        this.currentProcessRepository = currentProcessRepository;
        this.activityMapService = activityMapService;
    }

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
                        getFirstActivity(
                                services,
                                user,
                                applicationId,
                                data.getTxnId()
                        )
                                .flatMap(firstActivity -> {

                                    applicationFlowLogs.info(
                                            "First activity for applicationId {} txnId {} is {}",
                                            applicationId,
                                            data.getTxnId(),
                                            firstActivity.getActivityType()
                                    );

                                    return generate(
                                            firstActivity.getActivityType(),
                                            applicationId,
                                            request,
                                            txnId,
                                            Mono.just(data),
                                            new ApplicationFlowStatusEntity(),
                                            services,
                                            true,
                                            fromDraft,
                                            data.getUserId()
                                    );
                                })
                )
                .switchIfEmpty(
                        flow.switchIfEmpty(Mono.error(new SPRuntimeError("Invalid Form Request [H - 01]", HttpStatus.BAD_REQUEST,txnId)))
                            .flatMap(fl ->
                                generate(fl.getActivityType(), applicationId, request, txnId, Mono.empty(), fl,services,false,fromDraft,0l)
                             )
                );


    }

    private Mono<ActivityMapDTO.ActivityData> getFirstActivity(ServiceMeta service, UserSessionObject user, String applicationId, String txnId) {

        return activityMapService
                .getActivityMap(
                        service,
                        user,
                        applicationId,
                        txnId
                )
                .flatMap(activityMap -> {

                    if (activityMap == null || activityMap.getData() == null || activityMap.getData().isEmpty()) {
                        return Mono.error(new SPRuntimeError("Activity configuration not found [EX - 01]", HttpStatus.INTERNAL_SERVER_ERROR, txnId));
                    }


                    ActivityMapDTO.ActivityData firstActivity =
                            activityMap.getData()
                                    .stream()
                                    .filter(activity ->
                                            activity.getActivityType() != null && !activity.getActivityType().isBlank()
                                    )
                                    .min(
                                            Comparator.comparing(
                                                    ActivityMapDTO.ActivityData::getIndex,
                                                    Comparator.nullsLast(
                                                            Comparator.naturalOrder()
                                                    )
                                            )
                                    )
                                    .orElse(null);


                    if (firstActivity == null) {
                        return Mono.error(new SPRuntimeError("First activity not found [EX - 02]", HttpStatus.INTERNAL_SERVER_ERROR, txnId));
                    }


                    applicationFlowLogs.info(
                            "Resolved first activity for applicationId {} txnId {} " +
                                    "activityType {} activityName {} index {}",
                            applicationId,
                            txnId,
                            firstActivity.getActivityType(),
                            firstActivity.getActivityName(),
                            firstActivity.getIndex()
                    );

                    return Mono.just(firstActivity);
                });
    }

    public Mono<ServerResponse> generate(String statusKey, String applicationId, ServerRequest request, String txnId, Mono<TempTransactionLogs> fetch, ApplicationFlowStatusEntity flow, ServiceMeta service, boolean cache, boolean fromDraft, Long userId) {

        ApplicationFlowHandler handler = HandlerMapper.getHandler(statusKey);

        if (handler == null) {
            throw new IllegalArgumentException("No handler found for status: " + statusKey);
        }

        applicationFlowLogs.info("handler processed for applicationId {} txnId {} cache {} draft {} statusKey {} class {}", applicationId, txnId, cache, fromDraft, statusKey, handler.getClass());

        Mono<ApplicationFlowStatusEntity> flowMono;

        if (cache) {
            flowMono = Mono.just(flow);
        } else {

            flowMono = currentProcessRepository
                    .findByApplicationIdAndCurrentTaskAndActionTaken(
                            applicationId,
                            service.getTaskId(),
                            "N")
                    .map(currentProcess -> {
                        flow.setCurrentProcess(currentProcess);
                        return flow;
                    })
                    .switchIfEmpty(Mono.fromSupplier(() -> {
                        // First applicant task - CurrentProcess does not exist yet
                        flow.setCurrentProcess(null);
                        return flow;
                    }));
        }

        return flowMono.flatMap(updatedFlow -> {

            if (cache) {

                return next(statusKey, applicationId, request, fetch, updatedFlow, service, txnId, fromDraft, userId);

            } else if (fromDraft) {

                return transactionGeneration
                        .createNewTransactionAndUpdateInFlow(
                                service,
                                updatedFlow,
                                request.exchange().getRequest())
                        .flatMap(processingTxn ->
                                next(statusKey, applicationId, request, fetch, updatedFlow, service, processingTxn.getTxnId(), true, processingTxn.getUserId())
                        );

            } else {

                return processingTxnRepository
                        .findById(txnId)
                        .switchIfEmpty(Mono.error(
                                new SPRuntimeError("Invalid txnId", HttpStatus.BAD_REQUEST, txnId))
                        )
                        .flatMap(_txn ->
                                next(statusKey, applicationId, request, fetch, updatedFlow, service, txnId, false, _txn.getUserId())
                        );
            }
        });
    }

    public Mono<ServerResponse> next(String statusKey, String applicationId, ServerRequest request, Mono<TempTransactionLogs> fetch,
                                     ApplicationFlowStatusEntity flow, ServiceMeta service, String txnId, boolean fromDraft, Long userId) {

        ApplicationFlowHandler handler = HandlerMapper.getHandler(statusKey);

        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());

        if(isNull(user) || !userId.equals(user.getUserID())){
            return Mono.error(new SPRuntimeError("Unauthorized application access",HttpStatus.UNAUTHORIZED,txnId));
        }

        if(fromDraft){
            return handler.fetch(applicationId, request, statusKey, txnId, fetch, flow,service, true);
        }
        else{
            return handler.process(applicationId, request, statusKey, txnId, fetch, flow,service, false);
        }
    }

}
