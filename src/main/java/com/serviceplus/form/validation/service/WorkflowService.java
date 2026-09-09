package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SERVICE_WORKFLOW_REDIS_KEY_APPENDER;
import static com.serviceplus.form.validation.utility.ApplicationConstants.TYPE_GATEWAY;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.google.gson.reflect.TypeToken;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.Helpers.CurrentProcessBuilder;
import com.serviceplus.form.validation.Helpers.WorkflowHelper;
import com.serviceplus.form.validation.dto.EscalationDetailsDTO;
import com.serviceplus.form.validation.dto.InboxKafka;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.TaskRelationDTO;
import com.serviceplus.form.validation.dto.TaskAvailableOfficeLocation;
import com.serviceplus.form.validation.dto.TimePeriod;
import com.serviceplus.form.validation.dto.TimerTaskDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.entity.TimerTaskExecution;
import com.serviceplus.form.validation.entity.WorkflowEscalation;
import com.serviceplus.form.validation.enums.TaskType;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import com.serviceplus.form.validation.repository.EscalationRepository;
import com.serviceplus.form.validation.repository.TimerTaskExecutionRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class WorkflowService {

    @Autowired
    private CurrentProcessRepository currentProcessRepository;

    @Autowired
    private ReactiveApiClient apiClient;

    @Autowired
    private RedisService redis;

    @Autowired
    private WorkflowHelper workflowHelper;

    @Autowired
    private CurrentProcessBuilder currentProcessBuilder;

    @Autowired
    private TaskAssignmentService taskAssignmentService;

    @Autowired
    private GatewayService gatewayService;
    
    @Autowired
    private TimerTaskExecutionRepository timerTaskExecutionRepository;
    
    @Autowired
    private EscalationRepository escalationRepository;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    public void saveAndFlush(CurrentProcess c){
        currentProcessRepository.save(c);
    }

    public Mono<?> generateNextWorkflow(ApplicationDetails ad, ProcessingTxn savedLog, ServiceMeta service, UserSessionObject user, CurrentProcess cp) {
        return apiClient
				.fetchProcessFlow(service.getBaseServiceId(), user, savedLog.getApplicationId(), service.getTaskId(),
						service.getServiceId(), savedLog.getTxnId())
				.switchIfEmpty(Mono.error(new SPRuntimeError("Workflow Exception [ERR - 01]",
						HttpStatus.FAILED_DEPENDENCY, savedLog.getTxnId())))
				.flatMap(serviceJson -> generate(serviceJson.getProcessFlowMap(), serviceJson.getTimerTaskDetails(),serviceJson.getEscalationDetails(), ad,
						savedLog, service, user, cp))
				.onErrorResume(ex -> {
					ex.printStackTrace();
					return Mono.error(new SPRuntimeError("Workflow Error [ERR -01]", HttpStatus.INTERNAL_SERVER_ERROR,
							savedLog.getTxnId()));
				});
	}

    private Mono<?> generate(ServiceProcessFlowDTO response, List<TimerTaskDTO> timerTaskDetails,List<EscalationDetailsDTO> escalationDetails,ApplicationDetails ad, ProcessingTxn savedLog,
                             ServiceMeta service, UserSessionObject user, CurrentProcess cp) {
        List<ServiceProcessFlowDTO.Data> wf =  response.getData();
        String currentTask = service.getTaskId();

        applicationFlowLogs.info("Generating workflow for txnId {} currentTask {}",savedLog.getTxnId(),currentTask);

        ServiceProcessFlowDTO.Data data = workflowHelper.fetchNode(wf, currentTask);

        if(data != null) {
            applicationFlowLogs.info("Generating workflow for txnId {} currentTask {} nextNode {}"
                    ,savedLog.getTxnId(),currentTask,data.toString());

            return calculateNextWorkflow(data.getNode(), data, service, ad, savedLog, user, wf,cp,timerTaskDetails,escalationDetails,response.getTaskRelation());
        }

        return Mono.empty();
    }

    private Mono<InboxKafka> calculateNextWorkflow(ServiceProcessFlowDTO.Data.Nodes node, ServiceProcessFlowDTO.Data data,
                                                   ServiceMeta service, ApplicationDetails ad, ProcessingTxn txn, UserSessionObject user,
                                                   List<ServiceProcessFlowDTO.Data> wf, CurrentProcess currentActionProcess,List<TimerTaskDTO> timerTaskDetails,List<EscalationDetailsDTO> escalationDetails, Map<String, TaskRelationDTO> taskRelationMap) {

        ServiceProcessFlowDTO.Data.WorkflowElementData selectedWorkflow = service.getSelectedWorkflowElementData();

        LocalDateTime now = LocalDateTime.now();

        List<TaskAvailableOfficeLocation> taskAvailableOfficeLocations = new ArrayList<>();
        List<CurrentProcess> pList = new ArrayList<>();

        Map<String, Map<String, List<String>>> taskLocationUserHolderMap = new HashMap<>();
        Map<String, LocalDateTime> timerDueDate=new HashMap<String, LocalDateTime>();

        applicationFlowLogs.info("calculateNextWorkflow started txnId={}, currentNode={}, mappedTasks={}",
                txn.getTxnId(), node.getId(), data.getMappedTasks().size());

        return Flux.fromIterable(data.getMappedTasks())

                .concatMap(task -> {

                	ServiceProcessFlowDTO.Data.Nodes next = task.getNode();

                    applicationFlowLogs.info("Processing taskId={}, taskType={}, txnId={}", next.getId(),
                            next.getType(), txn.getTxnId());

                    return taskAssignmentService.nextAllowedOfficeLocation(wf, next, txn.getTxnId(), service.getServiceId(), user,
                            taskLocationUserHolderMap,service)

                            .flatMapMany(nextAllowedOfficeLocation -> {

                                applicationFlowLogs.info("nextAllowedOfficeLocation resolved for taskId={} : {}",
                                        next.getId(), nextAllowedOfficeLocation);

                                taskAvailableOfficeLocations.add(nextAllowedOfficeLocation);

                                applicationFlowLogs.info("Executing After Task MVEL for taskId={}, currentMap={}",
                                        next.getId(), taskLocationUserHolderMap);

                                return taskAssignmentService.executeAfterTaskMvel(user,service, ad, txn, "", currentActionProcess, next.getId(),
                                        taskLocationUserHolderMap,timerDueDate)

                                        .thenMany(Flux.defer(() -> {

                                            applicationFlowLogs.info(
                                                    "After Task MVEL completed for taskId={}, updatedMap={}",
                                                    next.getId(), taskLocationUserHolderMap);

                                            taskAssignmentService.refreshTaskAvailableOfficeLocation(nextAllowedOfficeLocation,
                                                    taskLocationUserHolderMap);

                                            applicationFlowLogs.info("Office locations refreshed for taskId={} : {}",
                                                    next.getId(), nextAllowedOfficeLocation);

                                            CurrentProcess baseProcess = currentProcessBuilder.buildBaseProcess(currentActionProcess, node,
                                                    next, service, ad, user, now, taskAvailableOfficeLocations, wf,
                                                    txn);

                                            applicationFlowLogs.info("Base process created for taskId={}, process={}",
                                                    next.getId(), baseProcess);

                                            pList.add(baseProcess);

                                            if (TYPE_GATEWAY.equals(next.getType())) {

                                                return gatewayService.processGateway(
                                                        node,
                                                        next,
                                                        wf,
                                                        service,
                                                        ad,
                                                        txn,
                                                        user,
                                                        now,
                                                        currentActionProcess,
                                                        baseProcess,
                                                        taskAvailableOfficeLocations,
                                                        taskLocationUserHolderMap,
                                                        selectedWorkflow,timerDueDate,taskRelationMap
                                                        );
                                            }

                                            return Flux.just(baseProcess);
                                        }));
                            });

                })

                .collectList()

				.flatMap(processList -> {

					applicationFlowLogs.info("Workflow processing completed. Generated process count={}",
							processList.size());

					processList.add(currentActionProcess);

					TaskRelationDTO taskRelationDTO = taskRelationMap.get(currentActionProcess.getCurrentTask());

					if (taskRelationDTO != null && taskRelationDTO.getNextTask() != null
							&& taskRelationDTO.getNextTask().size() > 1 && !pList.isEmpty()) {

						processList.add(pList.getFirst());
					}

					return saveTimerAndEscalationDetails(processList, timerDueDate, timerTaskDetails,escalationDetails)

							.then(Mono.fromSupplier(() -> {

								InboxKafka inboxKafkaDto = new InboxKafka();

								inboxKafkaDto.setProcessList(processList);
								inboxKafkaDto.setOfficeDetails(taskAvailableOfficeLocations);
								inboxKafkaDto.setServiceName(service.getServiceName());
								inboxKafkaDto.setAppliedBy(ad.getBeneficiaryId());
								inboxKafkaDto.setBeneficiaryName(ad.getBeneficiaryName());
								inboxKafkaDto.setApplyDate(ad.getApplyDate());
								inboxKafkaDto.setLoggedInUserId(user.getUserID());
								inboxKafkaDto.setLoggedInUserLocation(user.getLocationId());

								applicationFlowLogs.info(
										"InboxKafka prepared txnId={}, processCount={}, officeLocationCount={}",
										txn.getTxnId(), inboxKafkaDto.getProcessList().size(),
										inboxKafkaDto.getOfficeDetails().size());

								applicationFlowLogs.info("Final taskLocationUserHolderMap={}",
										taskLocationUserHolderMap);

								return inboxKafkaDto;
							}));
				}).doOnError(
						ex -> applicationFlowLogs.error("Error in calculateNextWorkflow txnId={}", txn.getTxnId(), ex));
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