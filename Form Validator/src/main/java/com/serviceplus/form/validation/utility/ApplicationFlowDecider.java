package com.serviceplus.form.validation.utility;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.Services;
import com.serviceplus.form.validation.dto.TaskActivity;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.repository.CustomQueryRepository;
import com.serviceplus.form.validation.service.ApplicationGenerationService;
import com.serviceplus.form.validation.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SERVICE_ACTIVITY_REDIS_KEY_APPENDER;
import static com.serviceplus.form.validation.utility.Utility.isEmpty;
import static java.util.Objects.isNull;

@Service
public class ApplicationFlowDecider {

    @Autowired
    private RedisService redis;

    @Autowired
    private ApplicationFlowRouter router;

    @Autowired
    private ApplicationFlowRouterRepository applicationFlowRouterRepository;

    @Autowired
    private ApplicationGenerationService applicationGenerationService;

    @Autowired
    private CustomQueryRepository customQueryRepository;

    public Mono<?> proceedToNext(String dataId, Services service, UserSessionObject user, ProcessingTxn txnLog, String appId
                                     , String actionCode, String from, ServerRequest reactiveRequestObject){

        if(isEmpty(from)){
            return Mono.error(new SPRuntimeError("Execution failure [EX -01]", HttpStatus.INTERNAL_SERVER_ERROR));
        }

        //FIRST ENTER ENTRY IN FLOW TABLE with completed == true  (from value)

        if(from.equals("FS") && txnLog.isNewEntity()){
            ApplicationFlowStatusEntity flowEntity = new ApplicationFlowStatusEntity();
            flowEntity.setApplicationId(appId);
            flowEntity.setFormId(service.getFormId());
            flowEntity.setProcessId(txnLog.getTxnId());
            flowEntity.setStatus("FS");
            flowEntity.setCompleted(false);
            flowEntity.setTenantId(user.getTenantId());
            flowEntity.setNewEntity(true);
            return applicationFlowRouterRepository.save(flowEntity)
                    .then(process(dataId, service, user, txnLog, appId, actionCode,from, reactiveRequestObject));
        }
        else{
            return process(dataId, service, user, txnLog, appId, actionCode,from, reactiveRequestObject);
        }
    }

    private Mono<?> process(String dataId, Services service, UserSessionObject user, ProcessingTxn txnLog, String appId
            , String actionCode, String from, ServerRequest reactiveRequestObject){
        return applicationFlowRouterRepository.updateCompletionNative(appId,txnLog.getTxnId(),from,true)
                .flatMap(response -> {
                    Mono<Object> fetch = redis.fetch(SERVICE_ACTIVITY_REDIS_KEY_APPENDER.concat("_")
                                    .concat(service.getServiceId().toString().concat("_").concat(service.getTaskId()))
                            , TaskActivity.class);

                    return fetch
                            .switchIfEmpty(Mono.error(new SPRuntimeError("Execution error [EX - 02]",HttpStatus.INTERNAL_SERVER_ERROR)))
                            .flatMap(activityMap -> {
                        TaskActivity activity = (TaskActivity) activityMap;
                        TaskActivity.ActivityData nextActivity= findNext(from, service.getTaskId(), activity);
                        if(isNull(nextActivity)){
                            return Mono.error(new SPRuntimeError("Execution error [EX - 03]",HttpStatus.INTERNAL_SERVER_ERROR));
                        }
                        else{
                            if(nextActivity.getActivityType().equals("NA")){
                                return applicationGenerationService.executeApplicationProcessing(dataId, service, user, txnLog, appId,actionCode);
                            }
                            else{
                                //FIRST ENTER ENTRY IN FLOW TABLE with completed == false
                                ApplicationFlowStatusEntity flowEntity = new ApplicationFlowStatusEntity();
                                flowEntity.setApplicationId(appId);
                                flowEntity.setFormId(service.getFormId());
                                flowEntity.setProcessId(txnLog.getTxnId());
                                flowEntity.setStatus(nextActivity.getActivityType());
                                flowEntity.setCompleted(false);
                                flowEntity.setTenantId(user.getTenantId());
                                flowEntity.setNewEntity(true);
                                return applicationFlowRouterRepository.save(flowEntity)
                                        .then(router.route(nextActivity.getActivityType(),appId,reactiveRequestObject,txnLog.getTxnId()));
                                // return  router.route(nextActivity.getActivityType(),appId,reactiveRequestObject,txnLog.getTxnId());
                            }
                        }

//                        if(from.equals("FS")){
//                            //IF NEXT IS internal like DG or mvel then called from here else return or save current process
//
//                            //Call handler api
//
//                        }
//                        return null;
                    });
                })
                .onErrorResume(Exception.class, ex -> {
                    ex.printStackTrace();
                    applicationFlowRouterRepository.updateCompletionNative(appId,txnLog.getTxnId(),from,false).subscribe();
                    //DELETE ALL THE BELOW ENTRIES OTHER THAN from but check if from FS or other need to evaluate????
                    Throwable actual = Exceptions.unwrap(ex);
                    if (actual instanceof SPRuntimeError spr) {
                        return Mono.error(new SPRuntimeError(spr.getMessage(), spr.getErrorCode()));
                    }
                    return Mono.error(new SPRuntimeError("Execution error [EX - 04]", HttpStatus.INTERNAL_SERVER_ERROR));
                });
    }

    private TaskActivity.ActivityData findNext(String from, String taskId, TaskActivity map){
        List<TaskActivity.ActivityData> data = map.getData();
        for(TaskActivity.ActivityData activity : data){
            if(activity.getActivityType().equals(from)){
                    if(activity.getLast()) {
                        return new TaskActivity.ActivityData("NA");
                    }
                    else{
                        return data.get(activity.getIndex() + 1);
                    }
            }
          }
        return null;
        }
}
