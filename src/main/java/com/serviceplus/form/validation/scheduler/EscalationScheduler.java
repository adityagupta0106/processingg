package com.serviceplus.form.validation.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.serviceplus.form.validation.service.EscalationSchedulerService;

@Component
public class EscalationScheduler {

	private static final Logger LOGGER = LoggerFactory.getLogger(EscalationScheduler.class);

	private final EscalationSchedulerService escalationSchedulerService;

	public EscalationScheduler(EscalationSchedulerService escalationSchedulerService) {
		this.escalationSchedulerService = escalationSchedulerService;
	}

	/**
	 * Executes every minute.
	 */
	@Scheduled(cron = "0 */15 * * * *")
	public void executePendingEscalations() {

	    LOGGER.info("Escalation Scheduler started.");

	    try {
	        escalationSchedulerService.processPendingEscalations()
	                .subscribe(
	                        result -> LOGGER.info("Escalation processing completed."),
	                        error -> LOGGER.error("Error while processing escalations.", error)
	                );

	    } catch (Exception ex) {
	        LOGGER.error("Error while executing escalation scheduler.", ex);
	    }

	    LOGGER.info("Escalation Scheduler completed.");
	}

}
