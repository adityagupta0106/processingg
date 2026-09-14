package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

@Repository
public interface ApplicationFlowRouterRepository  extends ReactiveCrudRepository<ApplicationFlowStatusEntity, String> {

    Mono<ApplicationFlowStatusEntity> findByApplicationIdAndCompletedAndTaskIdAndServiceIdAndTenantId(String applicationId,Integer completed,String taskId,Integer serviceId,String tenantId);

    Mono<ApplicationFlowStatusEntity> findByApplicationIdAndTxnIdAndCompletedAndTaskIdAndServiceIdAndTenantId(String applicationId,String txnId,Integer completed,String taskId,Integer serviceId,String tenantId);

    Mono<ApplicationFlowStatusEntity> findFirstByApplicationIdAndCompletedAndTaskIdAndServiceIdAndTenantIdAndActivityTypeOrderByIdDesc(
            String applicationId,
            Integer completed,
            String taskId,
            Integer serviceId,
            String tenantId,
            String activityType
    );

    Mono<ApplicationFlowStatusEntity> findByApplicationIdAndCompletedAndServiceIdAndTenantId(String applicationId,Integer completed,Integer serviceId,String tenantId);


    Mono<ApplicationFlowStatusEntity> findFirstByApplicationIdAndTaskIdAndActivityTypeAndCompletedOrderByIdDesc(String applicationId, String taskId, String type, int completed);

    Mono<ApplicationFlowStatusEntity> findFirstByApplicationIdAndTaskIdAndServiceIdAndTenantIdAndActivityTypeOrderByIdDesc(String appId, String taskId, Integer serviceId, String tenantId, String activityFormStatusKey);

    Mono<ApplicationFlowStatusEntity> findFirstByApplicationIdAndTaskIdAndServiceIdAndTenantIdAndActivityTypeAndLastUpdateAfterOrderByIdDesc(String appId, String taskId, Integer serviceId, String tenantId, String activityFormStatusKey, LocalDateTime initiatedOn);
}
