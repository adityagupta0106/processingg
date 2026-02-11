package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.entity.CurrentProcess;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface CurrentProcessRepository extends ReactiveCrudRepository<CurrentProcess, String> {

    Mono<CurrentProcess> findByServiceIdAndApplicationIdAndCurrentTaskAndActionTakenAndTenantId(Integer serviceId,String applicationId,String taskId,String actionTaken,String tenantId);
}
