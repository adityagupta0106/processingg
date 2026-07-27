package com.serviceplus.form.validation.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ApiExecutionResponse;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.executor.AssociatedTaskExecutor;

import reactor.core.publisher.Mono;

@Service
public class AssociateWebServiceExecutor implements AssociatedTaskExecutor {

	private static final Logger log = LogManager.getLogger("associateWebServiceLogger");

	private final WebServiceExecutionService webServiceExecutionService;

	private final TempTransactionLogService tempTransactionLogService;

	private final ReactiveApiClient reactiveApiClient;

	private final ObjectMapper objectMapper;

	public AssociateWebServiceExecutor(WebServiceExecutionService webServiceExecutionService,
			TempTransactionLogService tempTransactionLogService, ReactiveApiClient reactiveApiClient,
			ObjectMapper objectMapper) {
		this.webServiceExecutionService = webServiceExecutionService;
		this.tempTransactionLogService = tempTransactionLogService;
		this.reactiveApiClient = reactiveApiClient;
		this.objectMapper = objectMapper;
	}

	@Override
	public String getType() {
		return "webservice";
	}

	@Override
	public Mono<Void> execute(ServiceProcessFlowDTO.AssociatedActivity activity, CurrentProcess currentProcess,
			ApplicationDetails application, UserSessionObject user) {

		try {
			log.info("Executing Associated Web Service [{}]", activity.getId());
			Map<String, Object> attributeValues = new HashMap<>();
			return webServiceExecutionService
					.execute(activity.getWebServiceDetails(), attributeValues, application, currentProcess)
					.flatMap(response -> {
						try {
							return processResponse(response, activity, currentProcess, application, user);
						} catch (Exception e) {
							log.error("Failed to execute associated web service [{}]", activity.getId(), e);
							return Mono.error(new SPRuntimeError("Failed to process response from web service.",
									HttpStatus.INTERNAL_SERVER_ERROR, null));
						}
					});
		} catch (Exception ex) {
			log.error("Failed to execute associated web service [{}]", activity.getId(), ex);
			return Mono.error(new SPRuntimeError("Failed to execute associated web service.",
					HttpStatus.INTERNAL_SERVER_ERROR, null));
		}
	}

	private Mono<Void> processResponse(ApiExecutionResponse response, ServiceProcessFlowDTO.AssociatedActivity activity,
			CurrentProcess currentProcess, ApplicationDetails application, UserSessionObject user) throws Exception {

		if (activity.getWebServiceDetails() == null || activity.getWebServiceDetails().getFormDetail() == null) {
			return Mono.empty();
		}

		ServiceMeta service = new ServiceMeta();
		service.setServiceId(application.getServiceId());
		service.setBaseServiceId(currentProcess.getBaseServiceId());
		service.setCurrentProcessId(currentProcess.getProcessId());

		service.setTaskId(activity.getId());
		service.setFormId(activity.getWebServiceDetails().getFormDetail().getFormId());

		return tempTransactionLogService.mergeTransactionLog(service, user, null)
				.flatMap(txnLog -> {
					if (txnLog == null) {
						return Mono.empty();
					}
					return reactiveApiClient.fetchFormData(txnLog.getTxnId(), service, user)
							.flatMap(handler -> {
								String appData = buildAppData(handler.getData(), response);
								return reactiveApiClient.saveFormData(txnLog.getTxnId(), service, appData, user,
										currentProcess.getDataId(), application.getApplicationId()).then();
							});
				});
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
}
