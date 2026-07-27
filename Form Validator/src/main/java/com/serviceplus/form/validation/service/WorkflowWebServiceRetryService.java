package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.entity.WorkflowWebServiceExecution;

import reactor.core.publisher.Mono;

public interface WorkflowWebServiceRetryService {

    Mono<Void> resume(WorkflowWebServiceExecution execution);

}
