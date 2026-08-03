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
}
