package com.serviceplus.form.validation.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.serviceplus.form.validation.entity.WorkflowWebServiceExecution;

import reactor.core.publisher.Mono;

@Repository
public interface WorkflowWebServiceExecutionRepository
        extends ReactiveCrudRepository<WorkflowWebServiceExecution, Long> {

    Mono<WorkflowWebServiceExecution> findByStatusInAndNextRetryTimeLessThanEqual(
            List<String> status,
            Date nextRetryTime);
}
