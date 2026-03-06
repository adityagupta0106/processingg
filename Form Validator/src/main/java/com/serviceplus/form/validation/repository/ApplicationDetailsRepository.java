package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.entity.ApplicationDetails;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ApplicationDetailsRepository extends ReactiveCrudRepository<ApplicationDetails, String> {

    Mono<ApplicationDetails> findByApplicationIdAndTenantId(String applicationId,String tenantId);

    Mono<ApplicationDetails> findByTenantIdAndStatusAndReferenceNoInAndApplyDateBetween(
            String tenantId,
            String status,
            List<String> referenceNos,
            LocalDateTime from,
            LocalDateTime to
    );
}
