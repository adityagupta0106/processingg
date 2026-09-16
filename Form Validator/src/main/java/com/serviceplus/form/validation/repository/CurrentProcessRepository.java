package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.entity.CurrentProcess;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CurrentProcessRepository extends ReactiveCrudRepository<CurrentProcess, String> {

    Mono<CurrentProcess> findByServiceIdAndApplicationIdAndCurrentTaskAndActionTakenAndTenantId(Integer serviceId,String applicationId,String taskId,String actionTaken,String tenantId);

	Mono<CurrentProcess> findByProcessIdAndActionTaken(String id, String actionTaken);

    Mono<CurrentProcess> findByApplicationIdAndCurrentTaskAndActionTaken(String applicationId, String taskId, String action);
	
	Flux<CurrentProcess> findByServiceIdAndApplicationIdAndCurrentTaskInAndTenantId(Integer serviceId,
			String applicationId, List<String> taskIds, String tenantId);

	Flux<CurrentProcess> findByServiceIdAndApplicationIdAndPreviousProcessIdAndCurrentTaskInAndTenantId(Integer serviceId,
			String applicationId, String gatewayExecutionId, List<String> previousTasks, String tenantId);

	Mono<CurrentProcess> findByProcessIdAndApplicationIdAndTenantId(String triggeringProcessId, String applicationId,
			String tenantId);

    Mono<CurrentProcess> findByApplicationIdAndTenantId(String appId, String tenantId);

    Mono<Boolean> existsByApplicationIdAndTenantId(String appId, String tenantId);

    void findFirstByApplicationIdAndTenantId(String applicationId, String tenantId);
}
