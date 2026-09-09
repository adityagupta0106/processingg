package com.serviceplus.form.validation.scheduler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.serviceplus.form.validation.service.WorkflowWebServiceRetrySchedulerService;

@Component
public class WorkflowWebServiceRetryScheduler {
	private static final Logger log = LogManager.getLogger("webServiceSchedulerLogger");
    private final WorkflowWebServiceRetrySchedulerService schedulerService;

    public WorkflowWebServiceRetryScheduler(
            WorkflowWebServiceRetrySchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    //@Scheduled(cron = "0 0 * * * *")
    public void retry() {
    	log.info("Workflow Web Service Retry Scheduler started.");
        try {
            schedulerService.retryFailedExecutions()
                    .doOnSuccess(v ->
                            log.info("Workflow Web Service Retry Scheduler completed successfully."))
                    .doOnError(ex ->
                            log.error("Error occurred while retrying failed workflow web service executions.", ex))
                    .subscribe();
        } catch (Exception ex) {
            log.error("Unexpected exception while executing Workflow Web Service Retry Scheduler.", ex);
        }
    }
}
