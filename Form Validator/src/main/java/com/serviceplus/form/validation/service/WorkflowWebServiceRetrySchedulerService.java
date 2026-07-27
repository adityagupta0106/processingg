package com.serviceplus.form.validation.service;

import java.util.Arrays;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.repository.WorkflowWebServiceExecutionRepository;

import reactor.core.publisher.Mono;

@Service
public class WorkflowWebServiceRetrySchedulerService {
	private static final Logger log = LogManager.getLogger("webServiceSchedulerLogger");
    private final WorkflowWebServiceExecutionRepository repository;
    private final WorkflowWebServiceRetryService retryService;

    public WorkflowWebServiceRetrySchedulerService(
            WorkflowWebServiceExecutionRepository repository,
            WorkflowWebServiceRetryService retryService) {

        this.repository = repository;
        this.retryService = retryService;
    }

    public Mono<Void> retryFailedExecutions() {

        log.info("Fetching eligible workflow web service executions for retry.");

        return repository.findByStatusInAndNextRetryTimeLessThanEqual(
                        Arrays.asList(
                                "FAILED",
                                "API_SUCCESS",
                                "FORM_SUCCESS"),
                        new Date())
                .doOnNext(execution ->
                        log.info("Retrying executionId: {}, status: {}, attempt: {}",
                                execution.getExecutionId(),
                                execution.getStatus(),
                                execution.getAttemptCount()))
                .flatMap(retryService::resume)
                .doOnSuccess(v ->
                        log.info("Completed processing all eligible workflow web service executions."))
                .doOnError(ex ->
                        log.error("Error while processing workflow web service retries.", ex))
                .then();
    }
}
