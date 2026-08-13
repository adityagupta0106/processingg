package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.entity.CurrentProcess;

import java.util.List;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CurrentProcessRepository extends ReactiveCrudRepository<CurrentProcess, String> {

    Mono<CurrentProcess> findByServiceIdAndApplicationIdAndCurrentTaskAndActionTakenAndTenantId(Integer serviceId,String applicationId,String taskId,String actionTaken,String tenantId);

	Mono<CurrentProcess> findByIdAndActionTaken(String id, String actionTaken);

    Mono<CurrentProcess> findByApplicationIdAndCurrentTaskAndActionTaken(String applicationId, String taskId, String action);
	
	Flux<CurrentProcess> findByServiceIdAndApplicationIdAndCurrentTaskInAndTenantId(Integer serviceId,
			String applicationId, List<String> taskIds, String tenantId);

	Flux<CurrentProcess> findByServiceIdAndApplicationIdAndPreviousProcessIdAndCurrentTaskInAndTenantId(Integer serviceId,
			String applicationId, String gatewayExecutionId, List<String> previousTasks, String tenantId);

	Mono<CurrentProcess> findByProcessIdAndApplicationIdAndTenantId(String triggeringProcessId, String applicationId,
			String tenantId);
}
