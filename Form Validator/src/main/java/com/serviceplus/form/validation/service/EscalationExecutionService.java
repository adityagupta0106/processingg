package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.entity.WorkflowEscalation;

import reactor.core.publisher.Mono;

public interface EscalationExecutionService {

	public Mono<?> execute(WorkflowEscalation escalation);
}
