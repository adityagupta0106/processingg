package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.entity.ApplicationDocumentMergeEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ApplicationDocumentMergeRepository extends ReactiveCrudRepository<ApplicationDocumentMergeEntity, String> {

    Mono<ApplicationDocumentMergeEntity> findFirstByApplicationIdAndReferenceId(
            String applicationId,
            String referenceId
    );

    Flux<ApplicationDocumentMergeEntity> findByApplicationId(String applicationId);

    Flux<ApplicationDocumentMergeEntity> findByTxnId(String txnId);

    Mono<ApplicationDocumentMergeEntity> findFirstByApplicationIdAndProcessIdAndReferenceIdAndTenantIdOrderByCreatedOnDesc(String applicationId, String processId, String referenceId, String tenantId);

    Mono<ApplicationDocumentMergeEntity> findFirstByApplicationIdAndTaskIdAndReferenceIdAndTenantIdOrderByCreatedOnDesc(String applicationId, String taskId, String referenceId, String tenantId);

    Mono<ApplicationDocumentMergeEntity> findFirstByApplicationIdAndProcessIdAndReferenceIdAndTenantIdAndStatusOrderByCreatedOnDesc(String applicationId, String processId, String referenceId, String tenantId, String p);

    Mono<ApplicationDocumentMergeEntity> findFirstByApplicationIdAndTaskIdAndReferenceIdAndTenantIdAndStatusOrderByCreatedOnDesc(String applicationId, String taskId, String referenceId, String tenantId, String p);
}
