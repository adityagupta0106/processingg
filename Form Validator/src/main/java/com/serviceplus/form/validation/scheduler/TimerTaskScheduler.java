package com.serviceplus.form.validation.scheduler;

import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.serviceplus.form.validation.service.TimerTaskExecutionService;

@Component
public class TimerTaskScheduler {

	private static final Logger LOGGER = LogManager.getLogger(TimerTaskScheduler.class);

	private final TimerTaskExecutionService timerTaskExecutionService;

	public TimerTaskScheduler(TimerTaskExecutionService timerTaskExecutionService) {
		this.timerTaskExecutionService = timerTaskExecutionService;
	}

	@Scheduled(cron = "0 */15 * * * *")
	public void executeTimerTasks() {

		LOGGER.info("Timer Scheduler started at {}", new Date());
		timerTaskExecutionService.processPendingTimers().doOnSuccess(v -> LOGGER.info("Timer Scheduler completed"))
				.doOnError(ex -> LOGGER.error("Timer Scheduler failed", ex)).subscribe();
	}
}
