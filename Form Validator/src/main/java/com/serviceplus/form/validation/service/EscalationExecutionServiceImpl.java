package com.serviceplus.form.validation.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.serviceplus.form.validation.dto.EscalationDetailsDTO;
import com.serviceplus.form.validation.dto.EscalationMvelRequest;
import com.serviceplus.form.validation.dto.EscalationMvelResponse;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.TaskRelationDTO;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.WorkflowEscalation;
import com.serviceplus.form.validation.enums.EscalationStatus;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import com.serviceplus.form.validation.repository.EscalationRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Transactional
public class EscalationExecutionServiceImpl implements EscalationExecutionService {

	private static final Logger LOGGER = LogManager.getLogger("escalationSchedulerLogger");

	private final EscalationRepository escalationRepository;
	private final CurrentProcessRepository applicationCurrentProcessRepository;
	private final ApplicationDetailsRepository applicationDetailsRepository;
	private final ReactiveApiClient reactiveApiClient;
    private final TaskAssignmentService taskAssignmentService;
    private final WorkflowActionExecutor workflowActionExecutor;

	public EscalationExecutionServiceImpl(EscalationRepository escalationRepository,
			CurrentProcessRepository applicationCurrentProcessRepository,
			ApplicationDetailsRepository applicationDetailsRepository, ReactiveApiClient reactiveApiClient,TaskAssignmentService taskAssignmentService, WorkflowActionExecutor workflowActionExecutor) {
		this.escalationRepository = escalationRepository;
		this.applicationCurrentProcessRepository = applicationCurrentProcessRepository;
		this.applicationDetailsRepository = applicationDetailsRepository;
		this.reactiveApiClient = reactiveApiClient;
		this.taskAssignmentService=taskAssignmentService;
		this.workflowActionExecutor = workflowActionExecutor;
	}

	@Override
	public Mono<?> execute(WorkflowEscalation escalation) {

		ServiceMeta serviceMeta= new ServiceMeta();
		UserSessionObject user=new UserSessionObject();
		escalation.setStatus(EscalationStatus.IN_PROGRESS.name());
		String escalationCPId=escalation.getCurrentProcessId();
		return escalationRepository.save(escalation)

				.then(applicationCurrentProcessRepository.findByProcessIdAndActionTaken(escalation.getCurrentProcessId(), "N"))

				.switchIfEmpty(Mono.defer(() -> {

					LOGGER.info("Escalation {} cancelled. Current process {} is no longer active.", escalation.getId(),
							escalation.getCurrentProcessId());

					escalation.setStatus(EscalationStatus.CANCELLED.name());
					escalation.setModifiedOn(new Date());

					return escalationRepository.save(escalation).then(Mono.empty());
				}))

				.flatMap(currentProcess ->

				applicationDetailsRepository.findByApplicationId(escalation.getApplicationId())

						.switchIfEmpty(Mono.error(new RuntimeException("Application not found")))

						.flatMap(application -> {
						    user.setTenantId(application.getTenantId());

						    return reactiveApiClient
						            .fetchServiceMetadata(user, escalation.getServiceId(), "Escalation")
						            .flatMap(metadata -> {
						            	serviceMeta.setServiceId(metadata.getServiceId());
						            	serviceMeta.setServiceName(metadata.getServiceName());
						                Map<String, Map<String, List<String>>> taskLocationUserHolderMap = new HashMap<>();
						                List<String> nextNodeList = new ArrayList<>();
						                Map<String,TaskRelationDTO> taskRelationMap=metadata.getProcessFlowMap().getTaskRelation();
						                EscalationDetailsDTO escalationConfig = metadata.getEscalationDetails().stream()
						                        .filter(e -> e.getTaskId().equals(currentProcess.getCurrentTask()))
						                        .findFirst()
						                        .orElseThrow(() -> new RuntimeException("Escalation configuration not found"));

						                ServiceProcessFlowDTO processFlow = metadata.getProcessFlowMap();

						                ServiceProcessFlowDTO.Data workflowData = processFlow.getData().stream()
						                        .filter(data -> data.getNode().getId().equals(currentProcess.getCurrentTask()))
						                        .findFirst()
						                        .orElseThrow(() -> new RuntimeException(
						                                "Workflow node not found : " + currentProcess.getCurrentTask()));

						                return Flux.fromIterable(workflowData.getMappedTasks())
						                        .concatMap(currentTask -> {
						                            ServiceProcessFlowDTO.Data.Nodes next = currentTask.getNode();
						                            nextNodeList.add(next.getId());

						                            return taskAssignmentService.nextAllowedOfficeLocation(
						                                    processFlow.getData(),
						                                    next,
						                                    "Escalation_" + escalationCPId,
						                                    escalation.getServiceId(),
						                                    user,
						                                    taskLocationUserHolderMap,
						                                    serviceMeta);
						                        })
						                        .then(Mono.defer(() -> {
						                            EscalationMvelResponse escalationResponse = resolveEscalation(
						                                    escalationConfig,
						                                    application,
						                                    new HashMap<>(),
						                                    new HashMap<>(),
						                                    taskLocationUserHolderMap,
						                                    nextNodeList);

						                            return workflowActionExecutor.execute(
						                                    application,
						                                    currentProcess,
						                                    escalationResponse.getAction(),
						                                    escalationResponse.getTaskLocationUserHolderMap(),
						                                    escalationResponse.getNextNodeList(),
						                                    metadata,
						                                    null,
						                                    user,
						                                    serviceMeta,
						                                    taskRelationMap);
						                        }));
						            });
						}))
				.then(Mono.defer(() -> {
					escalation.setStatus(EscalationStatus.EXECUTED.name());
					return escalationRepository.save(escalation).then();
				}))
				.onErrorResume(ex -> {
					LOGGER.error("Escalation failed : {}", escalation.getId(), ex);
					escalation.setRetryCount(escalation.getRetryCount() == null ? 1 : escalation.getRetryCount() + 1);
					escalation.setStatus(EscalationStatus.FAILED.name());
					return escalationRepository.save(escalation).then();
				});
	}

	private EscalationMvelResponse resolveEscalation(EscalationDetailsDTO escalation, ApplicationDetails application,
			Map<String, Object> applicationDetails, Map<String, Object> serviceDetails,
			Map<String, Map<String, List<String>>> taskLocationUserHolderMap, List<String> nextNodeList) {

		EscalationMvelResponse response = new EscalationMvelResponse();

		if (escalation.getMvelExpression() == null || escalation.getMvelExpression().isBlank()) {

			response.setSuccess(true);
			response.setAction(escalation.getDefaultActions().get(0));
			response.setTaskLocationUserHolderMap(taskLocationUserHolderMap);
			response.setNextNodeList(nextNodeList);

			return response;
		}

		EscalationMvelRequest request = new EscalationMvelRequest();
		request.setExpression(escalation.getMvelExpression());
		request.setApplicationId(String.valueOf(application.getApplicationId()));
		request.setServiceId(application.getServiceId());
		request.setTaskId(escalation.getTaskId());
		request.setApplicationDetails(applicationDetails);
		request.setServiceDetails(serviceDetails);
		request.setTaskLocationUserHolderMap(taskLocationUserHolderMap);
		request.setNextNodeList(nextNodeList);

		EscalationMvelResponse mvelResponse = reactiveApiClient.executeEscalationMvel(request).block();

		if (mvelResponse == null || !mvelResponse.isSuccess()) {
			throw new RuntimeException("Escalation MVEL execution failed.");
		}

		if (mvelResponse.getAction() == null || mvelResponse.getAction().isBlank()) {
			throw new RuntimeException("Escalation MVEL did not return any action.");
		}

		if (!escalation.getDefaultActions().contains(mvelResponse.getAction())) {
			throw new RuntimeException("Invalid escalation action returned : " + mvelResponse.getAction());
		}

		return mvelResponse;
	}

}
