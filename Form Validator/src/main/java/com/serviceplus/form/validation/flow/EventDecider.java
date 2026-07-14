package com.serviceplus.form.validation.flow;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ActivityMapDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;
import com.serviceplus.form.validation.service.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SERVICE_ACTIVITY_REDIS_KEY_APPENDER;
import static com.serviceplus.form.validation.utility.Utility.isEmpty;
import static java.util.Objects.isNull;

@Service
public class EventDecider {

    @Autowired
    private RedisService redis;

    @Autowired
    private EventRouter router;

    @Autowired
    private ApplicationFlowRouterRepository applicationFlowRouterRepository;

    @Autowired
    private ApplicationGenerationService applicationGenerationService;

    @Autowired
    private ReactiveApiClient reactiveApiClient;

    @Autowired
    private TransactionGeneration transactionGeneration;

    @Autowired
    private ProcessingTxnRepository processingTxn;

    @Autowired
    private TransactionalDBExecutor transactionalDBExecutor;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    public Mono<ServerResponse> proceedToNext(String dataId, ServiceMeta service, UserSessionObject user, ProcessingTxn txnLog, String appId
            , String actionCode, String from, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus) {

        if (isEmpty(from)) {
            return Mono.error(new SPRuntimeError("Execution failure [EX -01]", HttpStatus.INTERNAL_SERVER_ERROR,txnLog.getTxnId()));
        }

        applicationFlowLogs.info("Next action for txnId {} applicationId {} ,from {} ,actionCode {} ,isNew {}"
                , txnLog.getTxnId(), appId, from, actionCode, txnLog.isNewEntity());

        if (isNull(flowStatus) || isEmpty(flowStatus.getId())) {
            applicationFlowLogs.info("For applId {} txnId {} flowStatus is empty with activityType {}",appId,txnLog.getTxnId(),from);
            return applicationFlowRouterRepository.findByApplicationIdAndTxnIdAndCompletedAndTaskIdAndServiceIdAndTenantId(
                            appId, txnLog.getTxnId(), 0, service.getTaskId(),service.getServiceId(), user.getTenantId()
                    )
                    .flatMap(flow -> preProcess(dataId, service, user, txnLog, appId
                            , actionCode, from, reactiveRequestObject, flow));
        } else {
            return preProcess(dataId, service, user, txnLog, appId
                    , actionCode, from, reactiveRequestObject, flowStatus);
        }
    }

    private Mono<ServerResponse> preProcess(String dataId, ServiceMeta service, UserSessionObject user, ProcessingTxn txnLog, String appId
            , String actionCode, String from, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus){

        flowStatus.setTxnId(txnLog.getTxnId());
        flowStatus.setCompleted(1);
        flowStatus.setDataId(dataId);
        flowStatus.setLastUpdate(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
        flowStatus.setTenantId(user.getTenantId());
        flowStatus.setApplicationId(appId);
        txnLog.setEndTime(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
        txnLog.setActivityType(flowStatus.getActivityType());

        return transactionalDBExecutor.execute(txnLog.getTxnId(),txnLog)
                .then(
                        process(dataId, service, user, txnLog, appId, actionCode,from, reactiveRequestObject,flowStatus)
                )
                .onErrorResume(Exception.class, ex -> {
                    ex.printStackTrace();
                    Throwable actual = Exceptions.unwrap(ex);
                    applicationFlowLogs.error("Error occurred for txnId {} applicationId {} is {}",txnLog.getTxnId(),appId,ex.getMessage());

                    return   Mono.error(
                            actual instanceof SPRuntimeError spr
                                    ? spr
                                    : new SPRuntimeError("Execution error [EX - 04]",
                                    HttpStatus.INTERNAL_SERVER_ERROR,txnLog.getTxnId())
                    );
                });
    }

    private Mono<ServerResponse> process(String dataId, ServiceMeta service, UserSessionObject user, ProcessingTxn txnLog, String appId
            , String actionCode, String from, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus) {
        Mono<Object> fetch = redis.fetch(SERVICE_ACTIVITY_REDIS_KEY_APPENDER.concat("_")
                        .concat(service.getServiceId().toString().concat("_").concat(service.getTaskId()))
                , ActivityMapDTO.class);

        return fetch
                .switchIfEmpty(
                        reactiveApiClient.fetchServiceKey(service.getBaseServiceId(), user, appId, service.getTaskId(), service.getServiceId())
                                .switchIfEmpty(Mono.error(new SPRuntimeError("Execution error [EX - 02]", HttpStatus.INTERNAL_SERVER_ERROR,txnLog.getTxnId())))
                                .flatMap(response -> Mono.just(response.getActivityMap()))
                )
                .flatMap(activityMap -> {
                	ActivityMapDTO activity = (ActivityMapDTO) activityMap;
                	ActivityMapDTO.ActivityData nextActivity = findNext(from, service.getTaskId(), activity);

                    if (isNull(nextActivity)) {
                        return Mono.error(new SPRuntimeError("Execution error [EX - 03]", HttpStatus.INTERNAL_SERVER_ERROR,txnLog.getTxnId()));
                    } else {

                        applicationFlowLogs.info("Next activity for txnId {} applicationId {} is {}", txnLog.getTxnId(), appId, nextActivity.toString());

                        if (nextActivity.getActivityType().equals("NA")) {
                            return applicationGenerationService.executeApplicationProcessing(dataId, service, user, txnLog, appId, actionCode)
                                    .flatMap(
                                            res -> markActivityAsDone(flowStatus).thenReturn(res)
                                    );
                        } else {
                            return transactionGeneration.createNewTransactionAndFlow(service, appId, nextActivity,
                                    user, reactiveRequestObject.exchange().getRequest(),null
                            ).flatMap(generatedTxn ->
                                            markActivityAsDone(flowStatus)
                                                    .thenReturn(generatedTxn))
                            .flatMap(
                                    generatedTxn -> router.route(nextActivity.getActivityType(), appId,
                                                                        reactiveRequestObject, generatedTxn.getTxnId(), service, false)
                            );
                        }
                    }
                });
    }

    private Mono<Void> markActivityAsDone(ApplicationFlowStatusEntity flowStatus) {
        return transactionalDBExecutor.execute(flowStatus.getTxnId(),flowStatus);
    }

    private ActivityMapDTO.ActivityData findNext(String from, String taskId, ActivityMapDTO map){
        List<ActivityMapDTO.ActivityData> data = map.getData();
        for(ActivityMapDTO.ActivityData activity : data){
            if(activity.getActivityType().equals(from)){
                    if(activity.getLast()) {
                        //return new TaskActivity.ActivityData("ES");
                        return new ActivityMapDTO.ActivityData("NA");
                    }
                    else{
                        return data.get(activity.getIndex() + 1);
                    }
            }
          }
        return null;
        }
}
