package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SERVICE_WORKFLOW_REDIS_KEY_APPENDER;
import static com.serviceplus.form.validation.utility.ApplicationConstants.TYPE_GATEWAY;

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
import com.serviceplus.form.validation.dto.InboxKafka;
import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.TaskRelationDTO;
import com.serviceplus.form.validation.dto.TaskAvailableOfficeLocation;
import com.serviceplus.form.validation.dto.TimerTaskDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.entity.TimerTaskExecution;
import com.serviceplus.form.validation.enums.TaskType;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
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
				.flatMap(serviceJson -> generate(serviceJson.getProcessFlowMap(), serviceJson.getTimerTaskDetails(), ad,
						savedLog, service, user, cp))
				.onErrorResume(ex -> {
					ex.printStackTrace();
					return Mono.error(new SPRuntimeError("Workflow Error [ERR -01]", HttpStatus.INTERNAL_SERVER_ERROR,
							savedLog.getTxnId()));
				});
	}

    private Mono<?> generate(ServiceProcessFlowDTO response, List<TimerTaskDTO> timerTaskDetails,ApplicationDetails ad, ProcessingTxn savedLog,
                             ServiceMeta service, UserSessionObject user, CurrentProcess cp) {
        List<ServiceProcessFlowDTO.Data> wf =  response.getData();
        String currentTask = service.getTaskId();

        applicationFlowLogs.info("Generating workflow for txnId {} currentTask {}",savedLog.getTxnId(),currentTask);

        ServiceProcessFlowDTO.Data data = workflowHelper.fetchNode(wf, currentTask);

        if(data != null) {
            applicationFlowLogs.info("Generating workflow for txnId {} currentTask {} nextNode {}"
                    ,savedLog.getTxnId(),currentTask,data.toString());

            return calculateNextWorkflow(data.getNode(), data, service, ad, savedLog, user, wf,cp,timerTaskDetails,response.getTaskRelation());
        }

        return Mono.empty();
    }

    private Mono<InboxKafka> calculateNextWorkflow(ServiceProcessFlowDTO.Data.Nodes node, ServiceProcessFlowDTO.Data data,
                                                   ServiceMeta service, ApplicationDetails ad, ProcessingTxn txn, UserSessionObject user,
                                                   List<ServiceProcessFlowDTO.Data> wf, CurrentProcess currentActionProcess,List<TimerTaskDTO> timerTaskDetails, Map<String, TaskRelationDTO> taskRelationMap) {

        ServiceProcessFlowDTO.Data.WorkflowElementData selectedWorkflow = service.getSelectedWorkflowElementData();

        LocalDateTime now = LocalDateTime.now();

        List<TaskAvailableOfficeLocation> taskAvailableOfficeLocations = new ArrayList<>();
        List<CurrentProcess> pList = new ArrayList<>();

        Map<String, Map<String, List<String>>> taskLocationUserHolderMap = new HashMap<>();
        Map<String, Date> timerDueDate=new HashMap<String, Date>();

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
                                                        selectedWorkflow,timerDueDate
                                                        );
                                            }

                                            return Flux.just(baseProcess);
                                        }));
                            });

                })

                .collectList()

                .map(processList -> {

                    applicationFlowLogs.info("Workflow processing completed. Generated process count={}",
                            processList.size());

                    processList.add(currentActionProcess);
                    //To add gateway process in current process by checking current action process task having multiple next task
                    TaskRelationDTO taskRelationDTO = taskRelationMap.get(currentActionProcess.getCurrentTask());
                    taskRelationDTO.getNextTask().size();
                    if (taskRelationDTO.getNextTask().size()>1 && !pList.isEmpty()) {
                        processList.add(pList.getFirst());
                    }
                    //
                    saveTimerTaskExecution(processList,timerDueDate,timerTaskDetails);
                    InboxKafka inboxKafkaDto = new InboxKafka();
                    inboxKafkaDto.setProcessList(processList);
                    inboxKafkaDto.setOfficeDetails(taskAvailableOfficeLocations);
                    inboxKafkaDto.setServiceName(service.getServiceName());
                    inboxKafkaDto.setOfficeDetails(taskAvailableOfficeLocations);
                    inboxKafkaDto.setServiceName(service.getServiceName());
                    inboxKafkaDto.setAppliedBy(ad.getBeneficiaryId());
                    inboxKafkaDto.setBeneficiaryName(ad.getBeneficiaryName());
                    inboxKafkaDto.setApplyDate(ad.getApplyDate());
                    inboxKafkaDto.setLoggedInUserId(user.getUserID());
                    inboxKafkaDto.setLoggedInUserLocation(user.getLocationId());

                    applicationFlowLogs.info("InboxKafka prepared txnId={}, processCount={}, officeLocationCount={}",
                            txn.getTxnId(), inboxKafkaDto.getProcessList().size(),
                            inboxKafkaDto.getOfficeDetails().size());

                    applicationFlowLogs.info("Final taskLocationUserHolderMap={}", taskLocationUserHolderMap);

                    return inboxKafkaDto;
                })

                .doOnError(
                        ex -> applicationFlowLogs.error("Error in calculateNextWorkflow txnId={}", txn.getTxnId(), ex));
    }
    
	private void saveTimerTaskExecution(List<CurrentProcess> processList,
			Map<String, Date> timerDueDate,List<TimerTaskDTO> timerTaskDetails) {

		if (processList == null || processList.isEmpty()) {
			return;
		}

		List<TimerTaskExecution> timerExecutionList = new ArrayList<>();

		for (CurrentProcess process : processList) {
			
			if(!TaskType.TIMER_TASK.getType().equals(process.getCurrentTaskType())) {
				continue;
			}
			TimerTaskDTO timerTask = timerTaskDetails.stream()
					.filter(t -> t.getTaskId().equals(process.getCurrentTask())).findFirst().orElse(null);

			if (timerTask == null) {
				continue;
			}

			TimerTaskExecution execution = new TimerTaskExecution();

			execution.setApplicationId(process.getApplicationId());
			execution.setCurrentProcessId(process.getProcessId());
			execution.setServiceId(process.getServiceId());
			execution.setBaseServiceId(process.getBaseServiceId());
			execution.setTaskId(process.getCurrentTask());
			execution.setStatus("PENDING");
			execution.setActionTaken("N");
			execution.setCreatedOn(new Date());

			Date dueDate = null;


			if (Integer.valueOf(2).equals(timerTask.getBehaviour())) {
				Calendar calendar = Calendar.getInstance();
				if ("Minute".equalsIgnoreCase(timerTask.getExecutionPeriodUnit())) {
					calendar.add(Calendar.MINUTE, timerTask.getExecutionPeriod());
				} else if ("Hour".equalsIgnoreCase(timerTask.getExecutionPeriodUnit())) {
					calendar.add(Calendar.HOUR, timerTask.getExecutionPeriod());
				} else if ("Day".equalsIgnoreCase(timerTask.getExecutionPeriodUnit())) {
					calendar.add(Calendar.DATE, timerTask.getExecutionPeriod());
				}

				dueDate = calendar.getTime();
			} else {
				dueDate = timerDueDate.get(process.getCurrentTask());
			}

			execution.setDueDate(dueDate);

			timerExecutionList.add(execution);
		}

		if (!timerExecutionList.isEmpty()) {
			timerTaskExecutionRepository.saveAll(timerExecutionList);
		}
	}

}