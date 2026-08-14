package com.serviceplus.form.validation.repository;

import java.time.LocalDateTime;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.serviceplus.form.validation.entity.TimerTaskExecution;

import reactor.core.publisher.Flux;

@Repository
public interface TimerTaskExecutionRepository
        extends ReactiveCrudRepository<TimerTaskExecution, String> {

    Flux<TimerTaskExecution> findByStatusAndDueDateLessThanEqual(
            String status,
            LocalDateTime now);
}
