package com.serviceplus.form.validation.scheduler;

import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.serviceplus.form.validation.service.TimerTaskExecutionService;

@Component
public class TimerTaskScheduler {

	private static final Logger applicationFlowLogger = LogManager.getLogger("applicationFlowLogger");

	private final TimerTaskExecutionService timerTaskExecutionService;

	public TimerTaskScheduler(TimerTaskExecutionService timerTaskExecutionService) {
		this.timerTaskExecutionService = timerTaskExecutionService;
	}

	@Scheduled(cron = "0 */15 * * * *")
	public void executeTimerTasks() {

		applicationFlowLogger.info("Timer Scheduler started at {}", new Date());
		timerTaskExecutionService.processPendingTimers().doOnSuccess(v -> applicationFlowLogger.info("Timer Scheduler completed"))
				.doOnError(ex -> applicationFlowLogger.error("Timer Scheduler failed", ex)).subscribe();
	}
}
