package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.FALLBACK_ACTION_NO;
import static com.serviceplus.form.validation.utility.ApplicationConstants.TYPE_GATEWAY;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.Utility.entityToString;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.serviceplus.form.validation.dto.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.Helpers.CurrentProcessBuilder;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.TaskRelationDTO;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.entity.TimerTaskExecution;
import com.serviceplus.form.validation.entity.WorkflowEscalation;
import com.serviceplus.form.validation.enums.TaskType;
import com.serviceplus.form.validation.kafka.KafkaProducer;
import com.serviceplus.form.validation.repository.EscalationRepository;
import com.serviceplus.form.validation.repository.TimerTaskExecutionRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class WorkflowActionExecutorImpl implements WorkflowActionExecutor {
	private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");
	private final CurrentProcessBuilder currentProcessBuilder;
	private final GatewayService gatewayService;
	private final ApplicationGenerationService applicationGenerationService;
	private final TaskAssignmentService taskAssignmentService;
    private final TimerTaskExecutionRepository timerTaskExecutionRepository;
    private final EscalationRepository escalationRepository;
    private final TransactionalDBExecutor transactionalDBExecutor;
    private final KafkaProducer kafkaProducer;
    @Value("${push.form.submission.data.inbox.topic}")
    private String PUSH_FORM_SUBMISSION_DATA_INBOX_TOPIC;
	public WorkflowActionExecutorImpl(TaskAssignmentService taskAssignmentService,
			CurrentProcessBuilder currentProcessBuilder, GatewayService gatewayService,
			ApplicationGenerationService applicationGenerationService, TimerTaskExecutionRepository timerTaskExecutionRepository,
			EscalationRepository escalationRepository,TransactionalDBExecutor transactionalDBExecutor,KafkaProducer kafkaProducer) {
		this.currentProcessBuilder = currentProcessBuilder;
		this.gatewayService = gatewayService;
		this.applicationGenerationService = applicationGenerationService;
		this.taskAssignmentService = taskAssignmentService;
		this.timerTaskExecutionRepository = timerTaskExecutionRepository;
		this.escalationRepository = escalationRepository;
		this.transactionalDBExecutor=transactionalDBExecutor;
		this.kafkaProducer=kafkaProducer;
	}

	@Override
	public Mono<InboxKafka> execute(ApplicationDetails application, CurrentProcess currentProcess, String action,
			Map<String, Map<String, List<String>>> taskLocationUserHolderMap, List<String> nextNodeList,
			ServiceJSONDTO serviceJson, ProcessingTxn txn, UserSessionObject user, ServiceMeta serviceMeta,
			Map<String, TaskRelationDTO> taskRelationMap) {

		currentProcess.setActionTaken("Y");
		currentProcess.setActionOn(LocalDateTime.now());
		currentProcess.setUserId(0L);
		currentProcess.setDataId(null);
		currentProcess.setFormId(null);
		currentProcess.setActionCode(action != null ? Integer.parseInt(action) : FALLBACK_ACTION_NO);
		ServiceProcessFlowDTO.Data.ActionAttribute actionAttribute = findActionAttribute(serviceJson,
				currentProcess.getCurrentTask(), action);
		currentProcess.setActionName(actionAttribute.getTrackLabel());

		boolean completeClosure = actionAttribute != null && Boolean.TRUE.equals(actionAttribute.getCompleteClosure());

		if (completeClosure) {

			return transactionalDBExecutor.execute(txn.getTxnId(), currentProcess, application, txn)
					.then(sendCurrentProcessToTracking(currentProcess, application, serviceMeta, user, true))
					.then(Mono.empty());
		}
		List<ServiceProcessFlowDTO.Data> wf = serviceJson.getProcessFlowMap().getData();

		ServiceProcessFlowDTO.Data currentData = wf.stream()
				.filter(data -> data.getNode().getId().equals(currentProcess.getCurrentTask())).findFirst().orElseThrow(
						() -> new RuntimeException("Workflow node not found : " + currentProcess.getCurrentTask()));

		ServiceProcessFlowDTO.Data.Nodes currentNode = currentData.getNode();

		LocalDateTime now = LocalDateTime.now();

		List<TaskAvailableOfficeLocation> officeLocations = new ArrayList<>();

		Map<String, LocalDateTime> timerDueDate = new HashMap<>();
		List<CurrentProcess> processList = new ArrayList<>();
		processList.add(currentProcess);
		ServiceProcessFlowDTO processFlow = serviceJson.getProcessFlowMap();

		if (taskLocationUserHolderMap == null) {
			taskLocationUserHolderMap = new HashMap<>();
		}

		if (nextNodeList == null) {
			nextNodeList = new ArrayList<>();
		}

		Map<String, Map<String, List<String>>> finalTaskLocationUserHolderMap = taskLocationUserHolderMap;

		List<String> finalNextNodeList = nextNodeList;

		return Flux.fromIterable(currentData.getMappedTasks())

				.filter(mappedTask -> finalNextNodeList.contains(mappedTask.getNode().getId()))

				.concatMap(mappedTask -> {

					ServiceProcessFlowDTO.Data.Nodes next = mappedTask.getNode();

					return taskAssignmentService
							.nextAllowedOfficeLocation(processFlow.getData(), next, txn.getTxnId(),
									serviceMeta.getServiceId(), user, finalTaskLocationUserHolderMap, serviceMeta)

							.doOnNext(officeLocation -> {
								officeLocations.add(officeLocation);
								taskAssignmentService.refreshTaskAvailableOfficeLocation(officeLocation,
										finalTaskLocationUserHolderMap);
							})

							.thenReturn(mappedTask);
				}).concatMap(mappedTask -> {

					ServiceProcessFlowDTO.Data.Nodes next = mappedTask.getNode();

					CurrentProcess baseProcess = currentProcessBuilder.buildBaseProcess(currentProcess, currentNode,
							next, serviceMeta, application, user, now, officeLocations, wf, txn);
					processList.add(baseProcess);
					if (TYPE_GATEWAY.equals(next.getType())) {

						return gatewayService.processGateway(currentNode, next, wf, serviceMeta, application, txn, user,
								now, currentProcess, baseProcess, officeLocations, finalTaskLocationUserHolderMap, null,
								timerDueDate, taskRelationMap,serviceJson.getOfficeDetails(),serviceJson.getApplicationRoutingMap());
					}

					return Mono.just(baseProcess);
				})

				.collectList()

				.flatMap(nextProcesses -> {

					processList.addAll(nextProcesses);
					TaskRelationDTO taskRelationDTO = taskRelationMap.get(currentProcess.getCurrentTask());

					if (taskRelationDTO != null && taskRelationDTO.getNextTask() != null
							&& taskRelationDTO.getNextTask().size() > 1 && !processList.isEmpty()) {

						processList.add(processList.getFirst());
					}
					return saveTimerAndEscalationDetails(processList, timerDueDate, serviceJson.getTimerTaskDetails(),
							serviceJson.getEscalationDetails())

							.then(Mono.defer(() -> {

								InboxKafka inboxKafka = new InboxKafka();

                                inboxKafka.setApplicantTaskDetails(buildApplicantTaskDetails(processList, wf));
								inboxKafka.setProcessList(processList);
								inboxKafka.setOfficeDetails(officeLocations);
								inboxKafka.setServiceName(serviceMeta.getServiceName());
								inboxKafka.setAppliedBy(application.getBeneficiaryId());
								inboxKafka.setBeneficiaryName(application.getBeneficiaryName());
								inboxKafka.setApplyDate(application.getApplyDate());
								inboxKafka.setLoggedInUserId(user.getUserID());
								inboxKafka.setLoggedInUserLocation(user.getLocationId());

								applicationFlowLogs.info(
										"InboxKafka prepared txnId={}, processCount={}, officeLocationCount={}",
										txn.getTxnId(), inboxKafka.getProcessList().size(),
										inboxKafka.getOfficeDetails().size());

								applicationFlowLogs.info("Final taskLocationUserHolderMap={}",
										finalTaskLocationUserHolderMap);

								return applicationGenerationService.persistWorkflow(application, inboxKafka, txn, user)

										.doOnSuccess(v -> applicationGenerationService.sendToInboxService(inboxKafka,
												application, serviceMeta))

										.thenReturn(inboxKafka);
							}));
				});
	}

    private List<ApplicantTaskDetails> buildApplicantTaskDetails(
            List<CurrentProcess> processList,
            List<ServiceProcessFlowDTO.Data> wf) {

        if (processList == null || processList.isEmpty()) {
            return Collections.emptyList();
        }

        return processList.stream()
                .filter(process ->
                        "N".equals(process.getActionTaken()))
                .filter(process ->
                        TaskType.APPLICANT_TASK.getType().equals(process.getCurrentTaskType()))

                .map(process ->
                        buildApplicantTaskDetail(
                                process,
                                wf
                        ))

                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ApplicantTaskDetails buildApplicantTaskDetail(CurrentProcess process, List<ServiceProcessFlowDTO.Data> wf) {

        ServiceProcessFlowDTO.Data taskData = wf.stream()
                .filter(Objects::nonNull)
                .filter(data -> data.getNode() != null)
                .filter(data ->
                        process.getCurrentTask()
                                .equals(data.getNode().getId()))
                .findFirst()
                .orElse(null);

        if (taskData == null || taskData.getNode().getApplicant() == null) {
            applicationFlowLogs.warn("Applicant configuration not found. taskId={}, applicationId={}", process.getCurrentTask(), process.getApplicationId());
            return null;
        }

        ServiceProcessFlowDTO.Data.Nodes node = taskData.getNode();

        ServiceProcessFlowDTO.Data.Applicant applicant = node.getApplicant();

        ApplicantTaskDetails details = new ApplicantTaskDetails();

        details.setCurrentProcessId(process.getProcessId());

        details.setTaskId(process.getCurrentTask());

        details.setApplicationId(process.getApplicationId());

        details.setSubmissionToSameOfficial(applicant.isSubmissionToSameOfficial());

        details.setUploadRejectedEnclosures(applicant.isUploadRejectedEnclosures());

        details.setDeoSubmit(applicant.isDeoSubmit());

        details.setRequiresForm(node.getFormId() != null && !node.getFormId().isBlank());

        details.setRequiresPayment(node.getPayment() != null && node.getPayment().getEnabled());

        return details;
    }
	
	private ServiceProcessFlowDTO.Data.ActionAttribute findActionAttribute(ServiceJSONDTO serviceJson,
			String currentTaskId, String action) {

		if (serviceJson == null || serviceJson.getProcessFlowMap() == null
				|| serviceJson.getProcessFlowMap().getData() == null || currentTaskId == null || action == null
				|| action.isBlank()) {
			return null;
		}

		return serviceJson.getProcessFlowMap().getData().stream()
				.filter(data -> data.getNode() != null && currentTaskId.equals(data.getNode().getId()))
				.map(ServiceProcessFlowDTO.Data::getWorkflowElementData).filter(java.util.Objects::nonNull)
				.map(ServiceProcessFlowDTO.Data.WorkflowElementData::getActionAttribute)
				.filter(java.util.Objects::nonNull).flatMap(List::stream)
				.filter(actionAttribute -> action.equals(actionAttribute.getKey())).findFirst().orElse(null);
	}

	public Mono<Void> sendCurrentProcessToTracking(CurrentProcess currentProcess, ApplicationDetails application,
			ServiceMeta service, UserSessionObject user, boolean completeClosure) {

		InboxKafka inboxKafka = new InboxKafka();

		inboxKafka.setProcessList(List.of(currentProcess));
		inboxKafka.setOfficeDetails(Collections.emptyList());

		inboxKafka.setServiceName(service.getServiceName());
		inboxKafka.setApplicationRefNo(application.getReferenceNo());

		inboxKafka.setAppliedBy(application.getBeneficiaryId());
		inboxKafka.setBeneficiaryName(application.getBeneficiaryName());
		inboxKafka.setApplyDate(application.getApplyDate());

		inboxKafka.setLoggedInUserId(user.getUserID());
		inboxKafka.setLoggedInUserLocation(user.getLocationId());

		inboxKafka.setCompleteClosure(completeClosure);

		String key = application.getApplicationId().concat("_").concat(UUID.randomUUID().toString());

		kafkaProducer.sendMessage(PUSH_FORM_SUBMISSION_DATA_INBOX_TOPIC, key, entityToString(inboxKafka));

		applicationFlowLogs.info("Terminal action. Current process pushed to tracking. applicationId={}, processId={}",
				application.getApplicationId(), currentProcess.getProcessId());

		return Mono.empty();
	}
	private Mono<Void> saveTimerAndEscalationDetails(List<CurrentProcess> processList,
			Map<String, LocalDateTime> timerDueDate, List<TimerTaskDTO> timerTaskDetails,
			List<EscalationDetailsDTO> escalationDetails) {

		return Mono.when(saveTimerTaskExecution(processList, timerDueDate, timerTaskDetails),
				saveWorkflowEscalations(processList, escalationDetails));
	}
	
	private Mono<Void> saveWorkflowEscalations(
	        List<CurrentProcess> processList,
	        List<EscalationDetailsDTO> escalationDetails) {

	    if (processList == null || processList.isEmpty()
	            || escalationDetails == null || escalationDetails.isEmpty()) {
	        return Mono.empty();
	    }

	    List<WorkflowEscalation> escalationList = new ArrayList<>();

	    for (CurrentProcess process : processList) {
	    	if (!"N".equals(process.getActionTaken())) {
	            continue;
	        }
	        EscalationDetailsDTO escalation = escalationDetails.stream()
	                .filter(e -> e.getTaskId().equals(process.getCurrentTask()))
	                .findFirst()
	                .orElse(null);

	        if (escalation == null) {
	            continue;
	        }

	        WorkflowEscalation workflowEscalation = new WorkflowEscalation();

	        workflowEscalation.setId(createUniqueId());
	        workflowEscalation.setApplicationId(process.getApplicationId());
	        workflowEscalation.setServiceId(process.getServiceId());
	        workflowEscalation.setCurrentProcessId(process.getProcessId());
	        workflowEscalation.setTaskId(process.getCurrentTask());
	        workflowEscalation.setStatus("PENDING");
	        workflowEscalation.setRetryCount(0);
	        workflowEscalation.setAction(null);
	        workflowEscalation.setMvelExpression(escalation.getMvelExpression());

	        workflowEscalation.setExecuteOn(calculateEscalationExecuteOn(escalation));

	        workflowEscalation.setCreatedOn(LocalDateTime.now());
	        workflowEscalation.setModifiedOn(LocalDateTime.now());
	        workflowEscalation.setNew(true);

	        escalationList.add(workflowEscalation);
	    }

	    if (escalationList.isEmpty()) {
	        return Mono.empty();
	    }

	    return escalationRepository
	            .saveAll(escalationList)
	            .doOnNext(saved ->
	                    applicationFlowLogs.info(
	                            "Workflow escalation saved successfully. id={}",
	                            saved.getId()))
	            .doOnError(ex ->
	                    applicationFlowLogs.error(
	                            "Failed to save workflow escalation records",
	                            ex))
	            .then();
	}
	
	private LocalDateTime calculateEscalationExecuteOn(
	        EscalationDetailsDTO escalation) {

	    LocalDateTime executeOn = LocalDateTime.now();

	    if (escalation == null
	            || escalation.getEscalationPeriod() == null) {
	        return executeOn;
	    }

	    TimePeriod period = escalation.getEscalationPeriod();

	    if (period.getDuration() == null
	            || period.getUnit() == null
	            || period.getUnit().getLabel() == null) {
	        throw new IllegalArgumentException(
	                "Escalation period duration and unit are required");
	    }

	    long duration;

	    try {
	        duration = Long.parseLong(period.getDuration());
	    } catch (NumberFormatException ex) {
	        throw new IllegalArgumentException(
	                "Invalid escalation period duration: "
	                        + period.getDuration(),
	                ex);
	    }

	    String unit = period.getUnit().getLabel();

	    if ("Minutes".equalsIgnoreCase(unit)) {
	        return executeOn.plusMinutes(duration);
	    }

	    if ("Hours".equalsIgnoreCase(unit)) {
	        return executeOn.plusHours(duration);
	    }

	    if ("Days".equalsIgnoreCase(unit)) {
	        return executeOn.plusDays(duration);
	    }

	    throw new IllegalArgumentException(
	            "Unsupported escalation period unit: " + unit);
	}
    
	private Mono<Void> saveTimerTaskExecution(List<CurrentProcess> processList,
			Map<String, LocalDateTime> timerDueDate,List<TimerTaskDTO> timerTaskDetails) {

		if (processList == null || processList.isEmpty()) {
			return Mono.empty();
		}

		List<TimerTaskExecution> timerExecutionList = new ArrayList<>();

		for (CurrentProcess process : processList) {
			if (!"N".equals(process.getActionTaken())) {
	            continue;
	        }
			if(!TaskType.TIMER_TASK.getType().equals(process.getCurrentTaskType())) {
				continue;
			}
			TimerTaskDTO timerTask = timerTaskDetails.stream()
					.filter(t -> t.getTaskId().equals(process.getCurrentTask())).findFirst().orElse(null);

			if (timerTask == null) {
				continue;
			}

			TimerTaskExecution execution = new TimerTaskExecution();
			execution.setId(createUniqueId());
			execution.setApplicationId(process.getApplicationId());
			execution.setCurrentProcessId(process.getProcessId());
			execution.setServiceId(process.getServiceId());
			execution.setBaseServiceId(process.getBaseServiceId());
			execution.setTaskId(process.getCurrentTask());
			execution.setStatus("PENDING");
			execution.setActionTaken("N");
			execution.setCreatedOn(LocalDateTime.now());
			execution.setNew(true);

			LocalDateTime dueDate = null;

			if (Integer.valueOf(2).equals(timerTask.getBehaviour())) {

			    LocalDateTime now = LocalDateTime.now();

			    if ("Minutes".equalsIgnoreCase(timerTask.getExecutionPeriodUnit())) {
			        dueDate = now.plusMinutes(timerTask.getExecutionPeriod());
			    } else if ("Hours".equalsIgnoreCase(timerTask.getExecutionPeriodUnit())) {
			        dueDate = now.plusHours(timerTask.getExecutionPeriod());
			    } else if ("Days".equalsIgnoreCase(timerTask.getExecutionPeriodUnit())) {
			        dueDate = now.plusDays(timerTask.getExecutionPeriod());
			    }

			} else {
			    dueDate = timerDueDate.get(process.getCurrentTask());
			}

			execution.setDueDate(dueDate);

			timerExecutionList.add(execution);
		}
		applicationFlowLogs.info("Timer execution records prepared: {}",timerExecutionList.size());
		if (timerExecutionList.isEmpty()) {
	        return Mono.empty();
	    }
		return timerTaskExecutionRepository
		            .saveAll(timerExecutionList)
		            .doOnNext(saved -> applicationFlowLogs.info(
		                    "Timer execution saved successfully. id={}",
		                    saved.getId()))
		            .doOnError(ex -> applicationFlowLogs.error(
		                    "Failed to save timer execution records",
		                    ex))
		            .then();
	}

}
