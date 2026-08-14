package com.serviceplus.form.validation.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import static com.serviceplus.form.validation.utility.ApplicationConstants.TYPE_GATEWAY;
import com.serviceplus.form.validation.Helpers.CurrentProcessBuilder;
import com.serviceplus.form.validation.dto.InboxKafka;
import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.Data;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.Data.Nodes;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.TaskRelationDTO;
import com.serviceplus.form.validation.dto.TaskAvailableOfficeLocation;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class WorkflowActionExecutorImpl implements WorkflowActionExecutor {

	private final CurrentProcessBuilder currentProcessBuilder;
	private final GatewayService gatewayService;
	private final ApplicationGenerationService applicationGenerationService;
	private final TaskAssignmentService taskAssignmentService;

	public WorkflowActionExecutorImpl(TaskAssignmentService taskAssignmentService,
			CurrentProcessBuilder currentProcessBuilder, GatewayService gatewayService,
			ApplicationGenerationService applicationGenerationService) {
		this.currentProcessBuilder = currentProcessBuilder;
		this.gatewayService = gatewayService;
		this.applicationGenerationService = applicationGenerationService;
		this.taskAssignmentService = taskAssignmentService;
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

		List<ServiceProcessFlowDTO.Data> wf = serviceJson.getProcessFlowMap().getData();

		ServiceProcessFlowDTO.Data currentData = wf.stream()
				.filter(data -> data.getNode().getId().equals(currentProcess.getCurrentTask())).findFirst().orElseThrow(
						() -> new RuntimeException("Workflow node not found : " + currentProcess.getCurrentTask()));

		ServiceProcessFlowDTO.Data.Nodes currentNode = currentData.getNode();

		LocalDateTime now = LocalDateTime.now();

		List<TaskAvailableOfficeLocation> officeLocations = new ArrayList<>();

		Map<String, LocalDateTime> timerDueDate = new HashMap<>();

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

					if (TYPE_GATEWAY.equals(next.getType())) {

						return gatewayService.processGateway(currentNode, next, wf, serviceMeta, application, txn, user,
								now, currentProcess, baseProcess, officeLocations, finalTaskLocationUserHolderMap, null,
								timerDueDate, taskRelationMap);
					}

					return Mono.just(baseProcess);
				})

				.collectList()

				.flatMap(nextProcesses -> {

					nextProcesses.add(currentProcess);

					InboxKafka inboxKafka = new InboxKafka();

					inboxKafka.setProcessList(nextProcesses);
					inboxKafka.setOfficeDetails(officeLocations);
					inboxKafka.setServiceName(serviceMeta.getServiceName());
					inboxKafka.setAppliedBy(application.getBeneficiaryId());
					inboxKafka.setBeneficiaryName(application.getBeneficiaryName());
					inboxKafka.setApplyDate(application.getApplyDate());
					inboxKafka.setLoggedInUserId(user.getUserID());
					inboxKafka.setLoggedInUserLocation(user.getLocationId());

					return applicationGenerationService.persistWorkflow(application, inboxKafka, txn, user)

							.doOnSuccess(v -> applicationGenerationService.sendToInboxService(inboxKafka, application,
									serviceMeta))

							.thenReturn(inboxKafka);
				});
	}

}
