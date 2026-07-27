package com.serviceplus.form.validation.service;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ApiExecutionRequest;
import com.serviceplus.form.validation.dto.ApiExecutionResponse;
import com.serviceplus.form.validation.dto.WebServiceDetails;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.executor.ApiExecutor;

import reactor.core.publisher.Mono;

@Service
public class WebServiceExecutionService {

	private final ApiExecutor reactiveApiClient;
    private final ObjectMapper objectMapper;
    @Value("${external.api.service}")
    private String externalApiBaseUrl;
    private static final Logger log = LogManager.getLogger("webServiceExecutorLogger");
    public WebServiceExecutionService(ApiExecutor reactiveApiClient,
                                      ObjectMapper objectMapper) {
        this.reactiveApiClient = reactiveApiClient;
        this.objectMapper = objectMapper;
    }

	public Mono<ApiExecutionResponse> execute(WebServiceDetails webserviceDetails,
			Map<String, Object> attributeValues, ApplicationDetails applicationDetails, CurrentProcess process) {

		try {

			ApiExecutionRequest request = new ApiExecutionRequest();
			request.setApplicationId(applicationDetails.getApplicationId());
			request.setCurrentProcessId(process.getId());
			request.setApiId(webserviceDetails.getDefinition().getId());
			request.setApiDefinition(webserviceDetails.getDefinition());
			request.setAttributeValues(attributeValues);
			request.setValidationRequired(Boolean.TRUE.equals(webserviceDetails.getDefinition().getServerValidation()));

			String body = objectMapper.writeValueAsString(request);

			return reactiveApiClient
					.callExternalEndpoint(String.class, HttpMethod.POST, null, null,
							externalApiBaseUrl + "/b/web-service/execute", body, MediaType.APPLICATION_JSON)
					.map(ResponseEntity::getBody).flatMap(response -> {
					    try {
					        return Mono.just(
					                objectMapper.readValue(
					                        response,
					                        ApiExecutionResponse.class));
					    } catch (JsonProcessingException ex) {
					        log.error("Failed to parse ApiExecutionResponse.", ex);
					        return Mono.error(
					                new SPRuntimeError(
					                        "Invalid response received from Web Service Executor.",
					                        HttpStatus.INTERNAL_SERVER_ERROR,
					                        null));
					    }
					});

		} catch (Exception ex) {
			log.error("Error while executing web service.", ex);
			return Mono.error(
					new SPRuntimeError("Unable to execute web service.", HttpStatus.INTERNAL_SERVER_ERROR, null));
		}
	}
}
