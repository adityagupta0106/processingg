package com.serviceplus.form.validation.service;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.serviceplus.form.validation.dto.TaskActivity;
import com.serviceplus.form.validation.utility.Utility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.reflect.TypeToken;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.Services;
import com.serviceplus.form.validation.dto.UserSessionObject;

import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SERVICE_ACTIVITY_REDIS_KEY_APPENDER;
import static com.serviceplus.form.validation.utility.Utility.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class ReactiveApiClient {

    @Autowired
    private ApiExecutor AsynchronousApiExecutor;

    @Value("${metatdata.service}")
    private String METADATA_SERVICE;

    @Value("${formmgmt.service}")
    private String FORM_MANAGEMENT_SERVICE;
    
    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private RedisService redis;

    @SuppressWarnings("unchecked")
	public Mono<List<Services>> fetchServiceList(UserSessionObject user) {
        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
        String url = METADATA_SERVICE.concat("apply/serviceList");

        Mono<ResponseEntity<String>> callExternalEndpoint = (Mono<ResponseEntity<String>>) AsynchronousApiExecutor.callExternalEndpoint(
												                String.class,
												                HttpMethod.GET,
												                headers,
												                Collections.emptyMap(),
												                url,
												                null,
												                MediaType.APPLICATION_JSON);
        
        
        //map if only transforming to list can be synchronous
        //IF another API call or DB or any other process then can use flatmap for asynchronous call
        return callExternalEndpoint.flatMap(apiResponse -> {
				                String body = apiResponse.getBody().toString();
				
				                Type listType = new TypeToken<List<Services>>() {}.getType();
				                List<Services> servicesList = (List<Services>) stringToEntityUsingType(body, listType);
				
				                return Mono.just(servicesList);
				            })
			        		;
    }

    @SuppressWarnings("unchecked")
	public Mono<Map<String,Object>> fetchFormData(String txnId, String formId) {
        String url = FORM_MANAGEMENT_SERVICE.concat("getByFormId?");

        Mono<ResponseEntity<String>> callExternalEndpoint = (Mono<ResponseEntity<String>>) AsynchronousApiExecutor.callExternalEndpoint(
											                String.class,
											                HttpMethod.GET,
											                Collections.emptyMap(),
											                Map.of("txnId", txnId, "formId", formId),
											                url,
											                null,
											                MediaType.APPLICATION_JSON);
        
       return callExternalEndpoint.flatMap(apiResponse -> {
                String body = apiResponse.getBody();
                if (body == null) {
                    return Mono.error(new SPRuntimeError("Unable to process your request", HttpStatus.FAILED_DEPENDENCY));
                }
                
                ObjectMapper mapper = new ObjectMapper();

                Map<String, Object> responseJson;
                try {
                    responseJson = mapper.readValue(body, new TypeReference<Map<String, Object>>() {});
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                    return Mono.error(new SPRuntimeError(
                        "Issue while processing the request [ERR - 002]", HttpStatus.INTERNAL_SERVER_ERROR));
                }
                
                responseJson.put("txnId", txnId);
                return Mono.just(responseJson);
            });
    }

    @SuppressWarnings("unchecked")
	public Mono<ResponseEntity<String>> saveFormData(String txnId, String formId, String appData) {
    	String url = FORM_MANAGEMENT_SERVICE.concat("addApplicationData");

        Mono<ResponseEntity<String>> callExternalEndpoint = (Mono<ResponseEntity<String>>) AsynchronousApiExecutor.callExternalEndpoint(
											                String.class,
											                HttpMethod.POST,
											                Collections.emptyMap(),
											                Map.of("txnId", txnId, "formId", formId),
											                url,
											                appData,
											                MediaType.APPLICATION_JSON);
        
       return callExternalEndpoint;
	}
    //CHECK CIRCUIT BREAKER AND ADD LOGS
    @SuppressWarnings("unchecked")
	public Mono<String> fetchReferenceAbbrviation(Integer serviceId, UserSessionObject user) {
        String url = METADATA_SERVICE.concat("serviceAbbreviation");

        Mono<ResponseEntity<String>> callExternalEndpoint = (Mono<ResponseEntity<String>>) AsynchronousApiExecutor.callExternalEndpoint(
											                String.class,
											                HttpMethod.GET,
											                Map.of("USER-DETAILS", entityToString(user)),
											                Map.of("serviceId", serviceId),
											                url,
											                null,
											                MediaType.APPLICATION_JSON);
        
       return callExternalEndpoint.flatMap(apiResponse -> {
                String body = apiResponse.getBody();
                
                Map<String, String> responseJson;
                try {
                    responseJson = mapper.readValue(body, new TypeReference<Map<String, String>>() {});
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                    return Mono.error(new SPRuntimeError(
                        "Issue while processing the request [SUB - 004]", HttpStatus.FAILED_DEPENDENCY));
                }

                String message = responseJson.getOrDefault("errorMessage", "");
                
                if(apiResponse.getStatusCode().is4xxClientError()) {
                	return Mono.error(new SPRuntimeError(
                			message + " - issue while processing [SUB - 005]",
                       HttpStatus.BAD_REQUEST));
                }
                else if(!apiResponse.getStatusCode().is2xxSuccessful()){
                    return Mono.error(new SPRuntimeError(
                    		message + " - issue while processing [SUB - 006]",
                        HttpStatus.UNPROCESSABLE_ENTITY));
                }
                
                if (body == null) {
                    return Mono.error(new SPRuntimeError("Unable to process your request [SUB - 007]", HttpStatus.FAILED_DEPENDENCY));
                }
                
                return Mono.just(body);
            });
    }

    @SuppressWarnings("unchecked")
    public Mono<ServerResponse> fetchServiceKey(Integer baseServiceId, UserSessionObject user, String appId, String taskId, Integer serviceId) {
        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
        String url = METADATA_SERVICE.concat("apply/resolveForm?");
        //CACHE AT THIS LEVEL !!!!!

        Mono<ResponseEntity<String>> callExternalEndpoint = (Mono<ResponseEntity<String>>) AsynchronousApiExecutor.callExternalEndpoint(
                                                                                String.class,
                                                                                HttpMethod.POST,
                                                                                headers,
                                                                                Map.of("baseServiceId",baseServiceId,"taskId",taskId,"serviceId",serviceId),
                                                                                url,
                                                                                null,
                                                                                MediaType.APPLICATION_JSON);


        return callExternalEndpoint.flatMap(apiResponse -> {
            String body = apiResponse.getBody();

            Type listType = new TypeToken<Services>() {}.getType();
            Services service = (Services) stringToEntityUsingType(body, listType);
            service.setServiceKey(encryptServiceKeys(service));
            TaskActivity taskActivity = service.getActivityMap();
            String key = SERVICE_ACTIVITY_REDIS_KEY_APPENDER.concat("_").concat(service.getServiceId().toString().concat("_").concat(service.getTaskId()));
            redis.add(taskActivity,key,false).subscribe();
            return ServerResponse.ok().bodyValue(service);
            })
            .onErrorResume(WebClientResponseException.class, Utility::handleWebClientError)
                ;
    }

    @SuppressWarnings("unchecked")
    public Mono<ServerResponse> fetchProcessFlow(Integer baseServiceId, UserSessionObject user, String appId, String taskId, Integer serviceId) {
        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
        String url = METADATA_SERVICE.concat("apply/processFlow?");

        Mono<ResponseEntity<String>> callExternalEndpoint = (Mono<ResponseEntity<String>>) AsynchronousApiExecutor.callExternalEndpoint(
                                                                String.class,
                                                                HttpMethod.POST,
                                                                headers,
                                                                Map.of("baseServiceId",baseServiceId,"taskId",taskId,"serviceId",serviceId),
                                                                url,
                                                                null,
                                                                MediaType.APPLICATION_JSON);


        return callExternalEndpoint.flatMap(apiResponse -> {
                    String body = apiResponse.getBody();

                    Type listType = new TypeToken<Services>() {}.getType();
                    Services service = (Services) stringToEntityUsingType(body, listType);
                    service.setServiceKey(encryptServiceKeys(service));
                    TaskActivity taskActivity = service.getActivityMap();
                    String key = SERVICE_ACTIVITY_REDIS_KEY_APPENDER.concat("_").concat(service.getServiceId().toString().concat("_").concat(service.getTaskId()));
                    redis.add(taskActivity,key,false).subscribe();
                    return ServerResponse.ok().bodyValue(service);
                })
                .onErrorResume(WebClientResponseException.class, Utility::handleWebClientError)
                ;
    }
}

