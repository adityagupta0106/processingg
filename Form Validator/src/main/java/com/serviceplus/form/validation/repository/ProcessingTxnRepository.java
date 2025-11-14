package com.serviceplus.form.validation.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.serviceplus.form.validation.entity.ProcessingTxn;
import reactor.core.publisher.Mono;

@Repository
public interface ProcessingTxnRepository extends ReactiveCrudRepository<ProcessingTxn, String> {

    Mono<ProcessingTxn> findByApplicationId(String applId);
}

