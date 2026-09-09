package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ActivityMapDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.ApplicationConstants.*;
import static java.util.Objects.isNull;

@Service
public class ActivityMapService {

    private final RedisService redis;
    private final ReactiveApiClient reactiveApiClient;

    public ActivityMapService(RedisService redis,
                              ReactiveApiClient reactiveApiClient) {
        this.redis = redis;
        this.reactiveApiClient = reactiveApiClient;
    }

    public Mono<ActivityMapDTO> getActivityMap(ServiceMeta service,
                                               UserSessionObject user,
                                               String applicationId,
                                               String txnId) {

        return reactiveApiClient
                .fetchServiceKey(
                        service.getBaseServiceId(),
                        user,
                        applicationId,
                        service.getTaskId(),
                        service.getServiceId()
                )
                .switchIfEmpty(Mono.error(
                        new SPRuntimeError(
                                "Execution error [EX - 02]",
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                txnId
                        )))
                .map(ServiceMeta::getActivityMap);
    }

    public ActivityMapDTO.ActivityData findNextActivity(String currentActivity,
                                                        ActivityMapDTO activityMap) {

        if (activityMap == null || activityMap.getData() == null) {
            return null;
        }

        for (ActivityMapDTO.ActivityData activity : activityMap.getData()) {

            if (currentActivity.equals(activity.getActivityType())) {

                if (Boolean.TRUE.equals(activity.getLast())) {
                    return new ActivityMapDTO.ActivityData("NA");
                }

                int nextIndex = activity.getIndex() + 1;

                if (nextIndex < activityMap.getData().size()) {
                    return activityMap.getData().get(nextIndex);
                }

                return null;
            }
        }

        return null;
    }

    public ActivityMapDTO.ActivityData findCurrentActivity(String taskId, String activityType, ActivityMapDTO activityMap) {

        if (isNull(activityMap.getData()) || activityMap.getData().isEmpty()) {
            return null;
        }

        return activityMap.getData()
                .stream()
                .filter(activity -> activityType.equals(activity.getActivityType()))
                .findFirst()
                .orElse(null);
    }

    public Mono<Boolean> isUserSubmissionRequired(ServiceMeta service,
                                                  UserSessionObject user,
                                                  String applicationId,
                                                  String txnId,
                                                  String activityType) {

        return getActivityMap(service, user, applicationId, txnId)
                .map(activityMap -> {

                    ActivityMapDTO.ActivityData currentActivity = findCurrentActivity(service.getTaskId(),activityType, activityMap);

                    if (currentActivity == null) {
                        throw new SPRuntimeError("Current activity not found in activity map.", HttpStatus.INTERNAL_SERVER_ERROR, txnId);
                    }

                    return Boolean.TRUE.equals(currentActivity.getUserSubmissionRequired());
                });
    }
    public Mono<Long> activityConfigId(ServiceMeta service,
    		UserSessionObject user,
    		String applicationId,
    		String txnId,
    		String activityType) {
    	
    	return getActivityMap(service, user, applicationId, txnId)
    			.map(activityMap -> {
    				ActivityMapDTO.ActivityData currentActivity = findCurrentActivity(service.getTaskId(),activityType, activityMap);
    				if (currentActivity == null) {
    					throw new SPRuntimeError("Current activity not found in activity map.", HttpStatus.INTERNAL_SERVER_ERROR, txnId);
    				}
    				return currentActivity.getActivityConfigId();
    			});
    }
}