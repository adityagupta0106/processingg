package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.entity.CurrentProcess;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface CurrentProcessRepository extends ReactiveCrudRepository<CurrentProcess, String> {

	Mono<CurrentProcess> findByServiceIdAndApplicationIdAndCurrentTaskAndActionTakenAndTenantId(Integer serviceId,
			String applicationId, String taskId, String actionTaken, String tenantId);

	Mono<CurrentProcess> findByProcessIdAndActionTaken(String id, String actionTaken);

	Mono<CurrentProcess> findByApplicationIdAndCurrentTaskAndActionTaken(String applicationId, String taskId,
			String action);

	Flux<CurrentProcess> findByServiceIdAndApplicationIdAndCurrentTaskInAndTenantId(Integer serviceId,
			String applicationId, List<String> taskIds, String tenantId);

	Flux<CurrentProcess> findByServiceIdAndApplicationIdAndPreviousProcessIdAndCurrentTaskInAndTenantId(
			Integer serviceId, String applicationId, String gatewayExecutionId, List<String> previousTasks,
			String tenantId);

	Mono<CurrentProcess> findByProcessIdAndApplicationIdAndTenantId(String triggeringProcessId, String applicationId,
			String tenantId);

    Mono<CurrentProcess> findByApplicationIdAndTenantId(String appId, String tenantId);

    Mono<Boolean> existsByApplicationIdAndTenantId(String appId, String tenantId);

    void findFirstByApplicationIdAndTenantId(String applicationId, String tenantId);
    //-------------------
	Flux<CurrentProcess> findByApplicationIdAndPreviousProcessIdAndTenantId(String applicationId,
			String previousProcessId, String tenantId);

	@Modifying
	@Query("""
	    UPDATE schm_sp.current_process
	       SET action_taken = 'Y',
	           action_code = :actionCode,
	           action_on = CURRENT_TIMESTAMP
	     WHERE process_id = :processId
	       AND application_id = :applicationId
	       AND tenant_id = :tenantId
	       AND action_taken = 'N'
	    """)
	Mono<Integer> closeProcessForCallback(
	        @Param("processId") String processId,
	        @Param("applicationId") String applicationId,
	        @Param("tenantId") String tenantId,
	        @Param("actionCode") Integer actionCode
	);
}
