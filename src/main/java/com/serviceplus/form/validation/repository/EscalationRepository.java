package com.serviceplus.form.validation.repository;

import java.time.LocalDateTime;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.serviceplus.form.validation.entity.WorkflowEscalation;

import reactor.core.publisher.Flux;

@Repository
public interface EscalationRepository extends ReactiveCrudRepository<WorkflowEscalation, String> {

	Flux<WorkflowEscalation> findByStatus(String status);

	Flux<WorkflowEscalation> findByStatusAndExecuteOnLessThanEqual(
	        String status,
	        LocalDateTime dueDate);

}
