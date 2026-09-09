package com.serviceplus.form.validation.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.TaskRelationDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;
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
	private final TempTransactionLogService tempTransactionLogService;
	private final PreProcessingFacade preProcessingFacade;

	public TimerTaskExecutionService(TimerTaskExecutionRepository timerRepository,
			CurrentProcessRepository currentProcessRepository,
			ApplicationDetailsRepository applicationDetailsRepository, ReactiveApiClient reactiveApiClient,
			TaskAssignmentService taskAssignmentService, WorkflowActionExecutor workflowActionExecutor,
			TempTransactionLogService tempTransactionLogService, PreProcessingFacade preProcessingFacade) {

		this.timerRepository = timerRepository;
		this.currentProcessRepository = currentProcessRepository;
		this.applicationDetailsRepository = applicationDetailsRepository;
		this.reactiveApiClient = reactiveApiClient;
		this.taskAssignmentService = taskAssignmentService;
		this.workflowActionExecutor = workflowActionExecutor;
		this.tempTransactionLogService = tempTransactionLogService;
		this.preProcessingFacade = preProcessingFacade;
	}

	public Mono<Void> processPendingTimers() {

		return timerRepository.findByStatusAndDueDateLessThanEqual("PENDING", LocalDateTime.now())

				.doOnNext(timer -> LOGGER.info("Pending timer found. id={}, applicationId={}, taskId={}, dueDate={}",
						timer.getId(), timer.getApplicationId(), timer.getTaskId(), timer.getDueDate()))

				.flatMap(this::executeTimer).then();
	}

	private Mono<Void> executeTimer(TimerTaskExecution timerExecution) {

		timerExecution.setStatus("IN_PROGRESS");
		timerExecution.setNew(false);

		return timerRepository.save(timerExecution).then(execute(timerExecution)).flatMap(executed -> {
			if (!executed) {
				return Mono.empty();
			}
			timerExecution.setStatus("EXECUTED");
			timerExecution.setActionTaken("Y");
			timerExecution.setExecutedOn(LocalDateTime.now());
			timerExecution.setNew(false);
			return timerRepository.save(timerExecution).then();
		}).onErrorResume(ex -> {
			LOGGER.error("Timer execution failed. timerId={}", timerExecution.getId(), ex);
			timerExecution.setStatus("FAILED");
			timerExecution.setNew(false);
			return timerRepository.save(timerExecution).then();
		});
	}

	private Mono<Boolean> execute(TimerTaskExecution timerExecution) {

		String currentProcessId = timerExecution.getCurrentProcessId();

		return currentProcessRepository.findByProcessIdAndActionTaken(currentProcessId, "N")

				.flatMap(currentProcess ->

				applicationDetailsRepository.findByApplicationId(timerExecution.getApplicationId())

						.switchIfEmpty(Mono.error(
								new RuntimeException("Application not found : " + timerExecution.getApplicationId())))

						.flatMap(application ->

						executeWithProcess(timerExecution, currentProcess, application)))

				.switchIfEmpty(Mono.defer(() -> {

					LOGGER.info("Timer {} cancelled. Current process {} is no longer active.", timerExecution.getId(),
							currentProcessId);

					timerExecution.setStatus("CANCELLED");
					timerExecution.setExecutedOn(LocalDateTime.now());
					timerExecution.setNew(false);

					return timerRepository.save(timerExecution).thenReturn(false);
				}));
	}

	private Mono<Boolean> executeWithProcess(TimerTaskExecution timerExecution, CurrentProcess currentProcess,
			ApplicationDetails application) {

		UserSessionObject user = new UserSessionObject();
		user.setTenantId(application.getTenantId());

		ServiceMeta serviceMeta = new ServiceMeta();

		serviceMeta.setServiceId(timerExecution.getServiceId());

		serviceMeta.setBaseServiceId(timerExecution.getBaseServiceId());

		serviceMeta.setCurrentProcessId(timerExecution.getCurrentProcessId());

		serviceMeta.setTaskId(timerExecution.getTaskId());
		
		serviceMeta.setFormId("");

		return reactiveApiClient.fetchServiceMetadata(user, timerExecution.getServiceId(), "Timer")

				.flatMap(metadata ->

				createTimerTransaction(timerExecution, currentProcess, application, user, serviceMeta)

						.flatMap(txn ->

						executeTimerWorkflow(timerExecution, currentProcess, application, user, metadata, serviceMeta,
								txn)));
	}

	
	private Mono<ProcessingTxn> createTimerTransaction(TimerTaskExecution timerExecution, CurrentProcess currentProcess,
			ApplicationDetails application, UserSessionObject user, ServiceMeta serviceMeta) {

		return tempTransactionLogService.mergeTransactionLog(serviceMeta, user, null)

				.switchIfEmpty(Mono.error(new RuntimeException("Unable to create transaction.")))

				.flatMap(txnLog ->

				preProcessingFacade.getFormDataAndSaveTxn(serviceMeta, user, null, txnLog,
						application.getApplicationId(), currentProcess.getDataId(), "FS", false, null, ""));
	}

	
	private Mono<Boolean> executeTimerWorkflow(TimerTaskExecution timerExecution, CurrentProcess currentProcess,
			ApplicationDetails application, UserSessionObject user, ServiceJSONDTO metadata, ServiceMeta serviceMeta,
			ProcessingTxn txn) {

		Map<String, Map<String, List<String>>> taskLocationUserHolderMap = new HashMap<>();

		List<String> nextNodeList = new ArrayList<>();

		ServiceProcessFlowDTO processFlow = metadata.getProcessFlowMap();

		Map<String, TaskRelationDTO> taskRelationMap = processFlow.getTaskRelation();

		String currentTask = currentProcess.getCurrentTask();

		ServiceProcessFlowDTO.Data workflowData = processFlow.getData().stream()
				.filter(data -> data.getNode().getId().equals(currentTask)).findFirst()
				.orElseThrow(() -> new RuntimeException("Workflow node not found : " + currentTask));

		return Flux.fromIterable(workflowData.getMappedTasks())

				.concatMap(task -> {

					ServiceProcessFlowDTO.Data.Nodes next = task.getNode();

					nextNodeList.add(next.getId());

					return taskAssignmentService.nextAllowedOfficeLocation(processFlow.getData(), next,
							txn.getTxnId(), timerExecution.getServiceId(), user,
							taskLocationUserHolderMap, serviceMeta);
				})

				.then(Mono.defer(() -> {

					LOGGER.info("Executing timer workflow. timerId={}, txnId={}, processId={}", timerExecution.getId(),
							txn.getTxnId(), currentProcess.getProcessId());

					return workflowActionExecutor.execute(application, currentProcess, timerExecution.getActionTaken(),
							taskLocationUserHolderMap, nextNodeList, metadata, txn,
							user, serviceMeta, taskRelationMap);
				}))

				.thenReturn(true);
	}
}