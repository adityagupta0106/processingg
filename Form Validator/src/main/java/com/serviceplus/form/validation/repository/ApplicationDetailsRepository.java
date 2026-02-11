package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.entity.ApplicationDetails;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ApplicationDetailsRepository extends ReactiveCrudRepository<ApplicationDetails, String> {

    Mono<ApplicationDetails> findByApplicationIdAndTenantId(String applicationId,String tenantId);
}
