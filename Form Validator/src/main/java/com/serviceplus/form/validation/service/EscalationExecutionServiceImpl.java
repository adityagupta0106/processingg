package com.serviceplus.form.validation.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.serviceplus.form.validation.utility.ApplicationConstants.TYPE_GATEWAY;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.Helpers.CurrentProcessBuilder;
import com.serviceplus.form.validation.Helpers.SystemAttributeHelper;
import com.serviceplus.form.validation.Helpers.WorkflowHelper;
import com.serviceplus.form.validation.dto.EscalationDetailsDTO;
import com.serviceplus.form.validation.dto.EscalationMvelRequest;
import com.serviceplus.form.validation.dto.EscalationMvelResponse;
import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.TaskRelationDTO;
import com.serviceplus.form.validation.dto.TaskAvailableOfficeLocation;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.entity.WorkflowEscalation;
import com.serviceplus.form.validation.enums.EscalationStatus;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import com.serviceplus.form.validation.repository.EscalationRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class EscalationExecutionServiceImpl implements EscalationExecutionService {

	private static final Logger LOGGER = LogManager.getLogger("escalationSchedulerLogger");

	private final EscalationRepository escalationRepository;
	private final CurrentProcessRepository currentProcessRepository;
	private final ApplicationDetailsRepository applicationDetailsRepository;
	private final ReactiveApiClient reactiveApiClient;
	private final TaskAssignmentService taskAssignmentService;
	private final WorkflowActionExecutor workflowActionExecutor;
	private final TempTransactionLogService tempTransactionLogService;
	private final PreProcessingFacade preProcessingFacade;
	private final GatewayService gatewayService;
	private final CurrentProcessBuilder currentProcessBuilder;
	private final WorkflowHelper workflowHelper;
	private final SystemAttributeHelper systemAttributeHelper;

	public EscalationExecutionServiceImpl(EscalationRepository escalationRepository,
			CurrentProcessRepository currentProcessRepository,
			ApplicationDetailsRepository applicationDetailsRepository, ReactiveApiClient reactiveApiClient,
			TaskAssignmentService taskAssignmentService, WorkflowActionExecutor workflowActionExecutor,
			TempTransactionLogService tempTransactionLogService, PreProcessingFacade preProcessingFacade,GatewayService gatewayService, CurrentProcessBuilder currentProcessBuilder,WorkflowHelper workflowHelper, SystemAttributeHelper systemAttributeHelper) {

		this.escalationRepository = escalationRepository;
		this.currentProcessRepository = currentProcessRepository;
		this.applicationDetailsRepository = applicationDetailsRepository;
		this.reactiveApiClient = reactiveApiClient;
		this.taskAssignmentService = taskAssignmentService;
		this.workflowActionExecutor = workflowActionExecutor;
		this.tempTransactionLogService = tempTransactionLogService;
		this.preProcessingFacade = preProcessingFacade;
		this.gatewayService=gatewayService;
		this.currentProcessBuilder = currentProcessBuilder;
		this.workflowHelper=workflowHelper;
		this.systemAttributeHelper = systemAttributeHelper;
	}

	@Override
	public Mono<?> execute(WorkflowEscalation escalation) {

		String escalationCPId = escalation.getCurrentProcessId();

		ServiceMeta serviceMeta = new ServiceMeta();

		UserSessionObject user = new UserSessionObject();

		return currentProcessRepository.findByProcessIdAndActionTaken(escalationCPId, "N")

				/*
				 * Current process no longer exists. Mark escalation as CANCELLED.
				 */
				.switchIfEmpty(Mono.defer(() -> {

					LOGGER.info("Escalation {} cancelled. Current process {} is no longer active.", escalation.getId(),
							escalationCPId);

					escalation.setNew(false);
					escalation.setStatus(EscalationStatus.CANCELLED.name());
					escalation.setModifiedOn(LocalDateTime.now());

					return escalationRepository.save(escalation).then(Mono.empty());
				}))

				.flatMap(currentProcess ->

				applicationDetailsRepository.findByApplicationId(escalation.getApplicationId())

						.switchIfEmpty(Mono.error(
								new RuntimeException("Application not found : " + escalation.getApplicationId())))

						.flatMap(application -> {

							user.setTenantId(application.getTenantId());

							return reactiveApiClient.fetchServiceMetadata(user, escalation.getServiceId(), "Escalation")

									.flatMap(metadata -> {

										serviceMeta.setServiceId(metadata.getServiceId());

										serviceMeta.setServiceName(metadata.getServiceName());

										/*
										 * Create ProcessingTxn before executing escalation workflow.
										 */
										return createEscalationTransaction(escalation, currentProcess, application,
												user, serviceMeta)

												.flatMap(txn -> {

													LOGGER.info(
															"ProcessingTxn created for escalation. escalationId={}, txnId={}, processId={}",
															escalation.getId(), txn.getTxnId(),
															currentProcess.getProcessId());

													return executeEscalationWorkflow(escalation, currentProcess,
															application, user, metadata, serviceMeta, txn);
												});
									});
						}))

				
				.then(Mono.defer(() -> {
					escalation.setNew(false);
					escalation.setStatus(EscalationStatus.EXECUTED.name());
					escalation.setModifiedOn(LocalDateTime.now());

					return escalationRepository.save(escalation).then();
				}))

				.onErrorResume(ex -> {

					LOGGER.error("Escalation failed : {}", escalation.getId(), ex);

					Integer retryCount = escalation.getRetryCount();

					escalation.setRetryCount(retryCount == null ? 1 : retryCount + 1);

					escalation.setNew(false);
					escalation.setStatus(EscalationStatus.FAILED.name());
					escalation.setModifiedOn(LocalDateTime.now());

					return escalationRepository.save(escalation).then();
				});
	}

	/**
	 * Creates a ProcessingTxn for escalation execution.
	 *
	 */
	private Mono<ProcessingTxn> createEscalationTransaction(WorkflowEscalation escalation,
			CurrentProcess currentProcess, ApplicationDetails application, UserSessionObject user,
			ServiceMeta serviceMeta) {

		serviceMeta.setServiceId(application.getServiceId());

		serviceMeta.setBaseServiceId(currentProcess.getBaseServiceId());

		serviceMeta.setCurrentProcessId(currentProcess.getProcessId());

		serviceMeta.setTaskId(currentProcess.getCurrentTask());
		
		serviceMeta.setFormId("");

		return tempTransactionLogService.mergeTransactionLog(serviceMeta, user, null)

				.switchIfEmpty(Mono.error(new RuntimeException("Unable to create escalation transaction.")))

				.flatMap(txnLog -> {

					LOGGER.info(
							"Creating ProcessingTxn for escalation. escalationId={}, txnId={}, applicationId={}, dataId={}",
							escalation.getId(), txnLog.getTxnId(), application.getApplicationId(),
							currentProcess.getDataId());

					return preProcessingFacade.getFormDataAndSaveTxn(serviceMeta, user, null, txnLog,
							application.getApplicationId(), currentProcess.getDataId(), "FS", false, null, "");
				});
	}

	/**
	 * Executes the escalation workflow after ProcessingTxn has been created.
	 */
	private Mono<?> executeEscalationWorkflow(WorkflowEscalation escalation, CurrentProcess currentProcess,
			ApplicationDetails application, UserSessionObject user, ServiceJSONDTO metadata, ServiceMeta serviceMeta,
			ProcessingTxn txn) {

		Map<String, Map<String, List<String>>> taskLocationUserHolderMap = new HashMap<>();

		List<String> nextNodeList = new ArrayList<>();

		Map<String, LocalDateTime> timerDueDate = new HashMap<>();

		Map<String, TaskRelationDTO> taskRelationMap = metadata.getProcessFlowMap().getTaskRelation();
		Map<String, Object> systemAttrMap = new HashMap<>();

		systemAttributeHelper.systemAttrMap(systemAttrMap,application,serviceMeta);

		/*
		 * Find escalation configuration for current task.
		 */
		EscalationDetailsDTO escalationConfig = metadata.getEscalationDetails().stream()
				.filter(e -> e.getTaskId().equals(currentProcess.getCurrentTask())).findFirst()
				.orElseThrow(() -> new RuntimeException(
						"Escalation configuration not found for task : " + currentProcess.getCurrentTask()));

		ServiceProcessFlowDTO processFlow = metadata.getProcessFlowMap();

		/*
		 * Find current workflow node.
		 */
		ServiceProcessFlowDTO.Data workflowData = processFlow.getData().stream()
				.filter(data -> data.getNode().getId().equals(currentProcess.getCurrentTask())).findFirst().orElseThrow(
						() -> new RuntimeException("Workflow node not found : " + currentProcess.getCurrentTask()));

		ServiceProcessFlowDTO.Data.Nodes currentNode = workflowData.getNode();

		LocalDateTime now = LocalDateTime.now();

		List<TaskAvailableOfficeLocation> officeLocations = new ArrayList<>();
		List<ServiceProcessFlowDTO.Data.Nodes> gatewayNodes =new ArrayList<>();

		/*
		 * Resolve next tasks.
		 */
		return Flux.fromIterable(workflowData.getMappedTasks())

				.flatMap(mappedTask -> {
					
					ServiceProcessFlowDTO.Data.Nodes next = mappedTask.getNode();

					nextNodeList.add(next.getId());

					/*
					 * First resolve office location.
					 */
					if (TYPE_GATEWAY.equals(next.getType())) {

	                    ServiceProcessFlowDTO.Data nextToGatewayData =
	                            workflowHelper.fetchNode(
	                                    processFlow.getData(),
	                                    next.getId());

	                    if (nextToGatewayData != null
	                            && nextToGatewayData.getMappedTasks() != null) {

	                        nextToGatewayData
	                                .getMappedTasks()
	                                .stream()
	                                .map(mappedNextTask ->
	                                        mappedNextTask
	                                                .getNode()
	                                                .getId())
	                                .forEach(nextNodeList::add);
	                    }

	                    gatewayNodes.add(next);
	                }
					return taskAssignmentService
							.nextAllowedOfficeLocation(processFlow.getData(), next,
									"Escalation_" + escalation.getCurrentProcessId(), escalation.getServiceId(), user,
									taskLocationUserHolderMap, serviceMeta)

							.flatMapMany(nextAllowedOfficeLocation -> {

								/*
								 * Keep office location for InboxKafka.
								 */
								officeLocations.add(nextAllowedOfficeLocation);

								return Flux.empty();
							});
				})

				/*
				 * After office locations and gateway processing, resolve escalation MVEL.
				 */
				.then(Mono.defer(() ->resolveEscalation(
		                escalationConfig,
		                application,
		                systemAttrMap,
		                new HashMap<>(),
		                taskLocationUserHolderMap,
		                nextNodeList)))

				/*
				 * Execute final workflow action using the actual ProcessingTxn.
				 */
				.flatMap(escalationResponse -> {
					escalation.setAction(escalationResponse.getAction());
				    escalation.setModifiedOn(LocalDateTime.now());
					LOGGER.info("Executing escalation workflow. escalationId={}, txnId={}, action={}, nextNodes={}",
							escalation.getId(), txn.getTxnId(), escalationResponse.getAction(),
							escalationResponse.getNextNodeList());

					return workflowActionExecutor.execute(application, currentProcess, escalationResponse.getAction(),
							escalationResponse.getTaskLocationUserHolderMap(), escalationResponse.getNextNodeList(),
							metadata, txn, user, serviceMeta, taskRelationMap);
				});
	}

	/**
	 * Resolves escalation action using MVEL.
	 *
	 */
	private Mono<EscalationMvelResponse> resolveEscalation(EscalationDetailsDTO escalation,
			ApplicationDetails application, Map<String, Object> applicationDetails, Map<String, Object> serviceDetails,
			Map<String, Map<String, List<String>>> taskLocationUserHolderMap, List<String> nextNodeList) {

		/*
		 * No MVEL configured. Use default action.
		 */
		if (escalation.getMvelExpression() == null || escalation.getMvelExpression().isBlank()) {

			EscalationMvelResponse response = new EscalationMvelResponse();

			response.setSuccess(true);
			response.setAction(escalation.getDefaultActions().get(0));
			response.setTaskLocationUserHolderMap(taskLocationUserHolderMap);
			response.setNextNodeList(nextNodeList);

			return Mono.just(response);
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
		LOGGER.info("Escalation nextNodeList before MVEL/default action = {}",nextNodeList);
		return reactiveApiClient.executeEscalationMvel(request)

				.switchIfEmpty(Mono.error(new RuntimeException("Escalation MVEL returned empty response.")))

				.flatMap(mvelResponse -> {

					if (!mvelResponse.isSuccess()) {
						return Mono.error(new RuntimeException("Escalation MVEL execution failed."));
					}

					if (mvelResponse.getAction() == null || mvelResponse.getAction().isBlank()) {

						return Mono.error(new RuntimeException("Escalation MVEL did not return any action."));
					}

					if (escalation.getDefaultActions() == null
							|| !escalation.getDefaultActions().contains(mvelResponse.getAction())) {

						return Mono.error(new RuntimeException(
								"Invalid escalation action returned : " + mvelResponse.getAction()));
					}
					LOGGER.info("Escalation response nextNodeList = {}",
							mvelResponse.getNextNodeList());
					return Mono.just(mvelResponse);
				});
	}
}