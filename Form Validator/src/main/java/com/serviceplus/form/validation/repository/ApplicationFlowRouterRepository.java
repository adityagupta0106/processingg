package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

@Repository
public interface ApplicationFlowRouterRepository  extends ReactiveCrudRepository<ApplicationFlowStatusEntity, String> {

    Mono<ApplicationFlowStatusEntity> findByApplicationIdAndProcessIdAndCompleted(String applicationId,String processId,boolean completed);

    Mono<ApplicationFlowStatusEntity> findByApplicationIdAndProcessIdAndStatusAndCompleted(String applicationId, String processId, String status,boolean completed);

    @Modifying
    @Query("""
    UPDATE schm_sp.application_flow_status 
    SET completed = :completion 
    WHERE application_id = :applicationId 
      AND process_id = :processId 
      AND status = :status
    """)
    Mono<Long> updateCompletionNative(
            @Param("applicationId") String applicationId,
            @Param("processId") String processId,
            @Param("status") String status,
            @Param("completion") boolean completion
    );

    //Mono<ApplicationFlowStatusEntity> updateCompletionByApplicationIdAndProcessIdAndStatus(String applicationId, String processId, String status);
}
