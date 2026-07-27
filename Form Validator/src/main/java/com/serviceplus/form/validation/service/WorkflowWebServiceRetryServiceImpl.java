package com.serviceplus.form.validation.service;

import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.entity.WorkflowWebServiceExecution;

import reactor.core.publisher.Mono;

@Service
public class WorkflowWebServiceRetryServiceImpl implements WorkflowWebServiceRetryService {

	private final WorkflowWebServiceTaskExecutor workflowWebServiceTaskExecutor;

	public WorkflowWebServiceRetryServiceImpl(WorkflowWebServiceTaskExecutor workflowWebServiceTaskExecutor) {
		this.workflowWebServiceTaskExecutor = workflowWebServiceTaskExecutor;
	}

	@Override
	public Mono<Void> resume(WorkflowWebServiceExecution execution) {
		
		switch (execution.getStatus()) {
		case "FAILED":
			return retryApi(execution);
//            case "API_SUCCESS":
//                return retryFormSubmission(execution);
//
//            case "FORM_SUCCESS":
//                return retryWorkflow(execution);

		default:
			return Mono.empty();
		}
	}

	/**
	 * External API was not completed.
	 */
	private Mono<Void> retryApi(WorkflowWebServiceExecution execution) {
		return workflowWebServiceTaskExecutor.resumeApiExecution(execution);
	}

//    /**
//     * API already succeeded.
//     * Resume from form submission.
//     */
//    private Mono<Void> retryFormSubmission(
//            WorkflowWebServiceExecution execution) {
//
//        return workflowWebServiceTaskExecutor
//                .resumeFormSubmission(execution);
//    }
//
//    /**
//     * Form already saved.
//     * Resume workflow generation.
//     */
//    private Mono<Void> retryWorkflow(
//            WorkflowWebServiceExecution execution) {
//
//        return workflowWebServiceTaskExecutor
//                .resumeWorkflow(execution);
//    }
}
