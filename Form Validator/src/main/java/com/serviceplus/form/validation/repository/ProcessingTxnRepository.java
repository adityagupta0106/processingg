package com.serviceplus.form.validation.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.serviceplus.form.validation.entity.ProcessingTxnEntity;

@Repository
public interface ProcessingTxnRepository extends ReactiveCrudRepository<ProcessingTxnEntity, String> {
}

