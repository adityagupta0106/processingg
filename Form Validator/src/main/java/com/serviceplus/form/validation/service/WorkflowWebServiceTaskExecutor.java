package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.Utility.entityToString;

import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ApiExecutionResponse;
import com.serviceplus.form.validation.dto.FormSubmissionResponse;
import com.serviceplus.form.validation.dto.InboxKafka;
import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.dto.WebServiceTaskDTO;
import com.serviceplus.form.validation.dto.WorkflowContext;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.entity.WorkflowWebServiceExecution;
import com.serviceplus.form.validation.enums.TaskType;
import com.serviceplus.form.validation.kafka.KafkaProducer;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import com.serviceplus.form.validation.repository.WorkflowWebServiceExecutionRepository;

import reactor.core.publisher.Mono;

@Service
public class WorkflowWebServiceTaskExecutor {

	private static final Logger log = LogManager.getLogger("workFlowWebServiceLogger");
	@Value("${push.form.submission.data.inbox.topic}")
    private String PUSH_FORM_SUBMISSION_DATA_INBOX_TOPIC;
	private final WebServiceExecutionService webServiceExecutionService;
	private final ReactiveApiClient apiClient;
	private final ObjectMapper objectMapper;
	private final TempTransactionLogService tempTransactionLogService;
	private final PreProcessingFacade preProcessingFacade;
	private final TransactionalDBExecutor transactionalDBExecutor;
	private final WorkflowService workflowService;
	private final ApplicationDetailsRepository applicationDetailsRepository;
	private final CurrentProcessRepository currentProcessRepository;
	private final WorkflowWebServiceExecutionRepository workflowExecutionRepository;
	private final AssociatedTaskService associatedTaskService;
	private final KafkaProducer kafkaProducer;

	public WorkflowWebServiceTaskExecutor(WebServiceExecutionService webServiceExecutionService,
			ReactiveApiClient apiClient, ObjectMapper objectMapper, TempTransactionLogService tempTransactionLogService,
			PreProcessingFacade preProcessingFacade,
			TransactionalDBExecutor transactionalDBExecutor,
			WorkflowService workflowService, CurrentProcessRepository currentProcessRepository,
			ApplicationDetailsRepository applicationDetailsRepository,
			WorkflowWebServiceExecutionRepository workflowExecutionRepository, AssociatedTaskService associatedTaskService, KafkaProducer kafkaProducer) {
		this.webServiceExecutionService = webServiceExecutionService;
		this.apiClient = apiClient;
		this.objectMapper = objectMapper;
		this.tempTransactionLogService = tempTransactionLogService;
		this.preProcessingFacade = preProcessingFacade;
		this.transactionalDBExecutor = transactionalDBExecutor;
		this.workflowService = workflowService;
		this.applicationDetailsRepository = applicationDetailsRepository;
		this.currentProcessRepository = currentProcessRepository;
		this.workflowExecutionRepository = workflowExecutionRepository;
		this.associatedTaskService = associatedTaskService;
		this.kafkaProducer = kafkaProducer;
	}

	public Mono<Object> execute(List<CurrentProcess> processList, ApplicationDetails applicationDetails,
			UserSessionObject user) {

		CurrentProcess process = processList.stream()
				.filter(cp -> TaskType.WEB_SERVICE_TASK.getType().equals(cp.getCurrentTaskType()))
				.filter(cp -> "N".equalsIgnoreCase(cp.getActionTaken())).findFirst().orElse(null);

		if (process == null) {
			return Mono.empty();
		}
		user.setUserID(0l);
		log.info("Executing workflow web service task [{}]", process.getCurrentTask());

		return apiClient.fetchServiceMetadata(user, process.getServiceId(), "WEB_SERVICE_TASK").flatMap(metadata -> {

			WebServiceTaskDTO task = metadata.getWebServiceTasks().stream()
					.filter(t -> process.getCurrentTask().equals(t.getTaskId())).findFirst()
					.orElseThrow(() -> new SPRuntimeError("Web Service Task configuration not found.",
							HttpStatus.INTERNAL_SERVER_ERROR, null));

			return createExecution(task, process, applicationDetails, user)

					.flatMap(execution -> webServiceExecutionService
							.execute(task.getWebserviceDetails(), new HashMap<String, Object>(), applicationDetails,
									process)
							.flatMap(response ->
							processResponse(response, task, process, applicationDetails, user, metadata)
									.then(updateCompleted(execution)))
							.onErrorResume(ex ->
							updateFailure(execution, ex).then(Mono.error(ex))));
		});

	}

	private Mono<?> processResponse(ApiExecutionResponse response, WebServiceTaskDTO task,
			CurrentProcess currentProcess, ApplicationDetails application, UserSessionObject user,
			ServiceJSONDTO serviceJson) {

		return submitWebServiceForm(response, task, currentProcess, application, user, serviceJson)

				.flatMap(submission ->

				processSubmission(submission, user, application)

						.flatMap(ctx ->

						completeCurrentProcess(ctx, currentProcess, application, user)));
	}

	private Mono<FormSubmissionResponse> submitWebServiceForm(ApiExecutionResponse response, WebServiceTaskDTO task,
			CurrentProcess currentProcess, ApplicationDetails application, UserSessionObject user,
			ServiceJSONDTO metadata) {

		ServiceMeta service = new ServiceMeta();
		String formId = metadata.getTaskFormMapping().get(task.getTaskId());
		service.setServiceId(application.getServiceId());
		service.setBaseServiceId(currentProcess.getBaseServiceId());
		service.setCurrentProcessId(currentProcess.getProcessId());

		service.setTaskId(task.getTaskId());
		service.setFormId(formId);
		
		return tempTransactionLogService.mergeTransactionLog(service, user, null)

				.flatMap(txnLog -> {
					if (txnLog == null) {
						return Mono.error(new SPRuntimeError("Unable to create transaction.",
								HttpStatus.INTERNAL_SERVER_ERROR, null));
					}
					return apiClient.fetchFormData(txnLog.getTxnId(), service, user)
							.flatMap(handler -> {
								String appData = buildAppData(handler.getData(), response);
								return apiClient
										.saveFormData(txnLog.getTxnId(), service, appData, user,
												currentProcess.getDataId(), application.getApplicationId())
										.map(saveResponse -> {
											FormSubmissionResponse result = new FormSubmissionResponse();
											result.setTxnId(txnLog.getTxnId());
											result.setService(service);
											result.setAppData(appData);
											result.setTempTransactionLogs(txnLog);
											result.setResponseBody(saveResponse.getBody());
											return result;
										});
							});
				});
	}

	private Mono<WorkflowContext> processSubmission(FormSubmissionResponse submission, UserSessionObject user,
			ApplicationDetails application) {
		Map<String, Object> responseJson;
		try {
			responseJson = objectMapper.readValue(submission.getResponseBody(),
					new TypeReference<Map<String, Object>>() {
					});

		} catch (Exception ex) {
			return Mono
					.error(new SPRuntimeError("Invalid Form response", HttpStatus.BAD_GATEWAY, submission.getTxnId()));
		}
		String dataId = String.valueOf(responseJson.get("dataId"));

		return preProcessingFacade.getFormDataAndSaveTxn(submission.getService(), user, null,
						submission.getTempTransactionLogs(), String.valueOf(application.getApplicationId()), dataId,
						"FS", false, null)
				.map(txn -> {
					WorkflowContext ctx = new WorkflowContext();
					ctx.setTxn(txn);
					ctx.setService(submission.getService());
					ctx.setDataId(dataId);
					return ctx;
				});
	}

	private Mono<?> completeCurrentProcess(WorkflowContext ctx, CurrentProcess currentProcess,
			ApplicationDetails application, UserSessionObject user) {

		currentProcess.setActionTaken("Y");
		currentProcess.setActionOn(LocalDateTime.now());
		currentProcess.setDataId(ctx.getDataId());
		currentProcess.setUserId(user.getUserID());
		currentProcess.setFormId(ctx.getService().getFormId());
		if (ctx.getService().getSelectedWorkflowElementData() != null
				&& ctx.getService().getSelectedWorkflowElementData().getActionAttribute() != null) {

			ServiceProcessFlowDTO.Data.ActionAttribute action = ctx.getService().getSelectedWorkflowElementData()
					.getActionAttribute().getFirst();

			if (Boolean.TRUE.equals(action.getCompleteClosure())) {

				return transactionalDBExecutor
						.execute(ctx.getTxn().getTxnId(), currentProcess, application, ctx.getTxn())

						.then(sendCurrentProcessToTracking(currentProcess, application,
								ctx.getService(), user));
			}
		}

		return workflowService.generateNextWorkflow(application, ctx.getTxn(), ctx.getService(), user, currentProcess)
				.flatMap(inbox ->
				persistWorkflow(application, (InboxKafka) inbox, ctx.getTxn(), user)
						.doOnSuccess(x -> sendToInboxService((InboxKafka) inbox,
								application, ctx.getService())));

	}
	
	public Mono<Void> persistWorkflow(
            ApplicationDetails ad,
            InboxKafka inboxKafka,
            ProcessingTxn txn,
            UserSessionObject user) {

        return transactionalDBExecutor.execute(txn.getTxnId(),inboxKafka.getProcessList(), ad, txn)
        		.then(Mono.fromRunnable(() ->
                associatedTaskService.executeAssociatedTasks(
                        inboxKafka.getProcessList(),
                        ad,
                        user)))
        .then(execute(
                inboxKafka.getProcessList(),
                ad,
                user)).then();
    }


    public Mono<Object> sendToInboxService(InboxKafka inboxKafka, ApplicationDetails appDetails,ServiceMeta service) {
        String key = appDetails.getApplicationId().concat("_").concat(UUID.randomUUID().toString());
        inboxKafka.setApplicationRefNo(appDetails.getReferenceNo());
        kafkaProducer.sendMessage(PUSH_FORM_SUBMISSION_DATA_INBOX_TOPIC,key,entityToString(inboxKafka));
        return Mono.just(true);
    }

	public Mono<Void> sendCurrentProcessToTracking(CurrentProcess currentProcess, ApplicationDetails application,
			ServiceMeta service, UserSessionObject user) {

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

		String key = application.getApplicationId().concat("_").concat(UUID.randomUUID().toString());

		kafkaProducer.sendMessage(PUSH_FORM_SUBMISSION_DATA_INBOX_TOPIC, key, entityToString(inboxKafka));

		log.info("Terminal action. Current process pushed to tracking. applicationId={}, processId={}",
				application.getApplicationId(), currentProcess.getProcessId());

		return Mono.empty();
	}
	@SuppressWarnings("unchecked")
	private String buildAppData(Map<String, Object> formDataResponse, ApiExecutionResponse response) {
		try {
			Map<String, Object> formData = (Map<String, Object>) formDataResponse.get("formData");
			if (formData == null) {
				throw new SPRuntimeError("Form data not found.", HttpStatus.INTERNAL_SERVER_ERROR, null);
			}
			for (Map.Entry<String, ApiExecutionResponse.DynamicAttributeData> entry : response.getNormalizedResponse()
					.entrySet()) {
				String attributeId = entry.getKey();
				ApiExecutionResponse.DynamicAttributeData data = entry.getValue();
				Object value = convertDynamicValue(data);
				formData.put(attributeId, value);
			}
			return objectMapper.writeValueAsString(formDataResponse);

		} catch (Exception ex) {
			ex.printStackTrace();
			throw new SPRuntimeError("Unable to prepare form data.", HttpStatus.INTERNAL_SERVER_ERROR, null);
		}
	}

	private Object convertDynamicValue(ApiExecutionResponse.DynamicAttributeData attribute) {

		List<Map<String, Object>> values = attribute.getData();

		if (values == null || values.isEmpty()) {
			return null;
		}

		if ("single".equalsIgnoreCase(attribute.getTargetType())) {

			if (values.size() == 1) {
				return values.get(0).get("value");
			}

			Map<String, Object> object = new LinkedHashMap<>();

			for (Map<String, Object> value : values) {
				object.put(String.valueOf(value.get("label")), value.get("value"));
			}

			return object;
		}

		return values;
	}

	public Mono<Void> resumeApiExecution(WorkflowWebServiceExecution execution) {

		Mono<ApplicationDetails> applicationMono = applicationDetailsRepository
				.findByApplicationId(execution.getApplicationId());

		Mono<CurrentProcess> processMono = currentProcessRepository.findById(execution.getCurrentProcessId());
		return Mono.zip(applicationMono, processMono).flatMap(tuple -> {
			ApplicationDetails application = tuple.getT1();
			CurrentProcess process = tuple.getT2();
			UserSessionObject user = new UserSessionObject();
			user.setTenantId(application.getTenantId());
			return execute(List.of(process), application, user).then();
		});
	}

	private Mono<WorkflowWebServiceExecution> createExecution(WebServiceTaskDTO task, CurrentProcess process,
			ApplicationDetails application, UserSessionObject user) {

		WorkflowWebServiceExecution execution = new WorkflowWebServiceExecution();
		execution.setApplicationId(application.getApplicationId());
		execution.setServiceId(process.getServiceId());
		execution.setBaseServiceId(process.getBaseServiceId());
		execution.setCurrentProcessId(process.getProcessId());
		execution.setCurrentTaskId(process.getCurrentTask());
		execution.setApiId(task.getWebserviceDetails().getDefinition().getId());
		execution.setStatus("FAILED");
		execution.setAttemptCount(1);
		execution.setMaxAttempt(Integer.parseInt(task.getWebserviceDetails().getWsCall().getWsCallFrequency()));
		execution.setRetryInterval(Integer.parseInt(task.getWebserviceDetails().getWsCall().getWsCallInterval()));
		execution.setRetryIntervalUnit(
				String.valueOf(task.getWebserviceDetails().getWsCall().getWsCallIntervalUnit().getValue()));
		execution.setNextRetryTime(calculateNextRetry(execution.getRetryInterval(), execution.getRetryIntervalUnit()));
		execution.setCreatedOn(new Date());
		execution.setModifiedOn(new Date());
		return workflowExecutionRepository.save(execution);
	}

	private Date calculateNextRetry(Integer interval, String unit) {

		Calendar calendar = Calendar.getInstance();
		switch (unit.toUpperCase()) {
		case "MINUTE":
			calendar.add(Calendar.MINUTE, interval);
			break;
		case "HOUR":
			calendar.add(Calendar.HOUR_OF_DAY, interval);
			break;
		case "DAY":
			calendar.add(Calendar.DATE, interval);
			break;
		}
		return calendar.getTime();
	}

	private Mono<Void> updateFailure(WorkflowWebServiceExecution execution, Throwable ex) {
		execution.setErrorMessage(ex.getMessage());
		execution.setModifiedOn(new Date());
		return workflowExecutionRepository.save(execution).then();
	}

	private Mono<Void> updateCompleted(WorkflowWebServiceExecution execution) {
		execution.setStatus("COMPLETED");
		execution.setModifiedOn(new Date());
		return workflowExecutionRepository.save(execution).then();
	}
}