package com.serviceplus.form.validation.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.TimerTaskExecution;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import com.serviceplus.form.validation.repository.TimerTaskExecutionRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class TimerTaskExecutionService {

	private static final Logger LOGGER = LogManager.getLogger("timerSchedulerLogger");

	private final TimerTaskExecutionRepository timerRepository;
	private final CurrentProcessRepository currentProcessRepository;
	private final ApplicationDetailsRepository applicationDetailsRepository;
	private final ReactiveApiClient reactiveApiClient;
	private final TaskAssignmentService taskAssignmentService;
	private final WorkflowActionExecutor workflowActionExecutor;

	public TimerTaskExecutionService(TimerTaskExecutionRepository timerRepository,
			CurrentProcessRepository currentProcessRepository,
			ApplicationDetailsRepository applicationDetailsRepository, ReactiveApiClient reactiveApiClient,
			TaskAssignmentService taskAssignmentService, WorkflowActionExecutor workflowActionExecutor) {

		this.timerRepository = timerRepository;
		this.currentProcessRepository = currentProcessRepository;
		this.applicationDetailsRepository = applicationDetailsRepository;
		this.reactiveApiClient = reactiveApiClient;
		this.taskAssignmentService = taskAssignmentService;
		this.workflowActionExecutor = workflowActionExecutor;
	}

	public Mono<Void> processPendingTimers() {
		return timerRepository.findByStatusAndDueDateLessThanEqual("PENDING", new Date()).flatMap(this::executeTimer)
				.then();
	}

	@SuppressWarnings("unused")
	private Mono<Void> executeTimer(TimerTaskExecution timerExecution) {
		timerExecution.setStatus("IN_PROGRESS");
		return timerRepository.save(timerExecution)
				.then(execute(timerExecution))
				.then(markExecuted(timerExecution))
				.onErrorResume(ex -> markFailed(timerExecution))
				.then();
	}

	public Mono<?> execute(TimerTaskExecution timerExecution) {
		ServiceMeta serviceMeta = new ServiceMeta();
		UserSessionObject user = new UserSessionObject();
		timerExecution.setStatus("IN_PROGRESS");
		String currentProcessId = timerExecution.getCurrentProcessId();

		return timerRepository.save(timerExecution)
				.then(currentProcessRepository.findByIdAndActionTaken(currentProcessId, "N"))
				.switchIfEmpty(Mono.defer(() -> {
					LOGGER.info("Timer {} cancelled. Current process {} is no longer active.", timerExecution.getId(),currentProcessId);
					timerExecution.setStatus("CANCELLED");
					timerExecution.setExecutedOn(new Date());
					return timerRepository.save(timerExecution).then(Mono.empty());
				}))
				.flatMap(currentProcess ->
				applicationDetailsRepository.findByApplicationId(timerExecution.getApplicationId())
						.switchIfEmpty(Mono.error(new RuntimeException("Application not found")))
						.flatMap(application -> {
							user.setTenantId(application.getTenantId());
							return reactiveApiClient
									.fetchServiceMetadata(user, timerExecution.getServiceId(), "Timer")
									.flatMap(metadata -> {
										serviceMeta.setServiceId(metadata.getServiceId());
										serviceMeta.setServiceName(metadata.getServiceName());
										Map<String, Map<String, List<String>>> taskLocationUserHolderMap = new HashMap<>();
										List<String> nextNodeList = new ArrayList<>();
										ServiceProcessFlowDTO processFlow = metadata.getProcessFlowMap();
										ServiceProcessFlowDTO.Data workflowData = processFlow.getData().stream()
												.filter(data -> data.getNode().getId().equals(currentProcess.getCurrentTask()))
												.findFirst()
												.orElseThrow(() -> new RuntimeException("Workflow node not found : "
														+ currentProcess.getCurrentTask()));

										return Flux.fromIterable(workflowData.getMappedTasks())

												.concatMap(task -> {
													ServiceProcessFlowDTO.Data.Nodes next = task.getNode();
													nextNodeList.add(next.getId());
													return taskAssignmentService.nextAllowedOfficeLocation(
															processFlow.getData(), next, "Timer_" + currentProcessId,
															timerExecution.getServiceId(), user,
															taskLocationUserHolderMap, serviceMeta);
												})
												.then(Mono.defer(() ->
												workflowActionExecutor.execute(application, currentProcess,
														timerExecution.getActionTaken(), taskLocationUserHolderMap,
														nextNodeList, metadata, null, user, serviceMeta)));
									});
						}))

				.then(Mono.defer(() -> {
					timerExecution.setStatus("EXECUTED");
					timerExecution.setExecutedOn(new Date());
					return timerRepository.save(timerExecution).then();
				}))

				.onErrorResume(ex -> {
					LOGGER.error("Timer execution failed : {}", timerExecution.getId(), ex);

					timerExecution.setStatus("FAILED");

					return timerRepository.save(timerExecution).then();
				});
	}

	private Mono<TimerTaskExecution> markExecuted(TimerTaskExecution timerExecution) {
		timerExecution.setStatus("EXECUTED");
		timerExecution.setExecutedOn(new Date());
		return timerRepository.save(timerExecution);
	}

	private Mono<TimerTaskExecution> markFailed(TimerTaskExecution timerExecution) {
		timerExecution.setStatus("FAILED");
		return timerRepository.save(timerExecution);
	}
}
