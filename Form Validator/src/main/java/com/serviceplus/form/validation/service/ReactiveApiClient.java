package com.serviceplus.form.validation.service;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.executor.ApiExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactivestreams.Publisher;
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

import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.ApplicationConstants.*;
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
    
    @Value("${mvel.execution.service}")
    private String MVEL_EXECUTION_SERVICE;

    @Value("${tracking.service}")
    private String TRACKING_SERVICE;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private RedisService redis;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    @SuppressWarnings("unchecked")
	public Mono<List<ServiceMeta>> fetchServiceList(UserSessionObject user) {
        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
        String url = METADATA_SERVICE.concat("apply/serviceList");

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
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
				
				                Type listType = new TypeToken<List<ServiceMeta>>() {}.getType();
				                List<ServiceMeta> servicesList = (List<ServiceMeta>) stringToEntityUsingType(body, listType);
				
				                return Mono.just(servicesList);
				            })
			        		;
    }

    @SuppressWarnings("unchecked")
	public Mono<HandlerResponse> fetchFormData(String txnId, ServiceMeta service, UserSessionObject user) {
        String url = FORM_MANAGEMENT_SERVICE.concat("getByFormId?");
        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
											                String.class,
											                HttpMethod.GET,
                                                            headers,
											                Map.of("txnId", txnId, "formId", service.getFormId()
                                                                    ,"taskId",service.getTaskId(),"serviceId",service.getServiceId()
                                                            ),
											                url,
											                null,
											                MediaType.APPLICATION_JSON);
        
       return callExternalEndpoint.flatMap(apiResponse -> {
                String body = apiResponse.getBody();
                if (body == null || body.isBlank()) {
                    return Mono.error(new SPRuntimeError("Unable to process your request", HttpStatus.FAILED_DEPENDENCY,txnId));
                }
                
                ObjectMapper mapper = new ObjectMapper();

                Map<String, Object> responseJson;
                try {
                    responseJson = mapper.readValue(body, new TypeReference<Map<String, Object>>() {});
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                    return Mono.error(new SPRuntimeError(
                        "Issue while processing the request [ERR - 002]", HttpStatus.INTERNAL_SERVER_ERROR,txnId));
                }
                HandlerResponse hr = new HandlerResponse();
                hr.setData(responseJson);
                hr.setTxnId(txnId);
                hr.setActivityType(ACTIVITY_FORM_STATUS_KEY);
                hr.setApplyLocations(service.getLocations());
                return Mono.just(hr);
            });
    }

	public Mono<ResponseEntity<String>> saveFormData(String txnId, ServiceMeta service, String appData, UserSessionObject user, String dataId,String applId) {
    	String url = FORM_MANAGEMENT_SERVICE.concat("addApplicationData");
        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
        dataId = isEmpty(dataId) ? "" : dataId;

        return AsynchronousApiExecutor.callExternalEndpoint(
											                String.class,
											                HttpMethod.POST,
                                                            headers,
											                Map.of("txnId", txnId, "formId", service.getFormId(),
                                                                 "serviceId",service.getServiceId(),"taskId",service.getTaskId(),"dataId",dataId,"applId",applId
                                                             ),
											                url,
											                appData,
											                MediaType.APPLICATION_JSON);
	}
    //CHECK CIRCUIT BREAKER AND ADD LOGS
	public Mono<String> fetchReferenceAbbrviation(Integer serviceId, UserSessionObject user, String txnId) {
        String url = METADATA_SERVICE.concat("serviceAbbreviation");

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
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
                    responseJson = mapper.readValue(body, new TypeReference<>() {
                    });
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                    return Mono.error(new SPRuntimeError(
                        "Issue while processing the request [SUB - 004]", HttpStatus.FAILED_DEPENDENCY,txnId));
                }

                String message = responseJson.getOrDefault("errorMessage", "");
                
                if(apiResponse.getStatusCode().is4xxClientError()) {
                	return Mono.error(new SPRuntimeError(
                			message.concat(" - issue while processing [SUB - 005]"),
                       HttpStatus.BAD_REQUEST,txnId));
                }
                else if(!apiResponse.getStatusCode().is2xxSuccessful()){
                    return Mono.error(new SPRuntimeError(
                    		message.concat(" - issue while processing [SUB - 006]"),
                        HttpStatus.UNPROCESSABLE_ENTITY,txnId));
                }
                
                if (body == null) {
                    return Mono.error(new SPRuntimeError("Unable to process your request [SUB - 007]", HttpStatus.FAILED_DEPENDENCY,txnId));
                }
                
                return Mono.just(body);
            });
    }

    public Mono<ServiceMeta> fetchServiceKey(Integer baseServiceId, UserSessionObject user, String appId, String taskId, Integer serviceId) {
        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
        String url = METADATA_SERVICE.concat("apply/resolveForm?");
        //CACHE AT THIS LEVEL ?????

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
                                                                                String.class,
                                                                                HttpMethod.POST,
                                                                                headers,
                                                                                Map.of("baseServiceId",baseServiceId,"taskId",taskId,"serviceId",serviceId),
                                                                                url,
                                                                                null,
                                                                                MediaType.APPLICATION_JSON);


        return callExternalEndpoint.flatMap(apiResponse -> {
            String body = apiResponse.getBody();

            Type listType = new TypeToken<ServiceMeta>() {}.getType();
            ServiceMeta service = (ServiceMeta) stringToEntityUsingType(body, listType);
            service.setServiceKey(encryptServiceKeys(service));
            TaskActivity taskActivity = service.getActivityMap();
            String key = SERVICE_ACTIVITY_REDIS_KEY_APPENDER.concat("_").concat(service.getServiceId().toString().concat("_").concat(service.getTaskId()));
            redis.add(taskActivity,key,true,5).subscribe();
            return Mono.just(service);
            })
            .onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex,"FROM FETCH SERVICE LIST"));

    }

    public Mono<ServiceWorkFlow> fetchProcessFlow(Integer baseServiceId, UserSessionObject user, String appId, String taskId, Integer serviceId,String txnId) {
        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
        String url = METADATA_SERVICE.concat("apply/processFlow?");

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
                                                                String.class,
                                                                HttpMethod.POST,
                                                                headers,
                                                                Map.of("serviceId",serviceId),
                                                                url,
                                                                null,
                                                                MediaType.APPLICATION_JSON);


        return callExternalEndpoint.flatMap(apiResponse -> {
                    String body = apiResponse.getBody();

                    Type listType = new TypeToken<ServiceWorkFlow>() {}.getType();
                    ServiceWorkFlow workflow = (ServiceWorkFlow) stringToEntityUsingType(body, listType);
                    final String REDIS_KEY =SERVICE_WORKFLOW_REDIS_KEY_APPENDER.concat("_").concat(baseServiceId.toString());
                    redis.add(workflow,REDIS_KEY,true,5).subscribe();
                    return Mono.just(workflow);
                })
                .onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex,txnId))
                ;
    }

    public Mono<ServerResponse> fetchApplicantData(String dataId, String formId,UserSessionObject user,String txnId,String applicationId) {
        String url = FORM_MANAGEMENT_SERVICE.concat("getApplicationData?");
        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
                String.class,
                HttpMethod.POST,
                headers,
                Collections.emptyMap(),
                url,
                entityToString(Map.of( "formId", formId,"dataId",dataId,"txnId",txnId)),
                MediaType.APPLICATION_JSON);

        return callExternalEndpoint.flatMap(apiResponse -> {
            String body = apiResponse.getBody();
            if (body == null || body.isBlank()) {
                return Mono.error(new SPRuntimeError("Unable to process your request", HttpStatus.FAILED_DEPENDENCY,txnId));
            }

            ObjectMapper mapper = new ObjectMapper();

            Map<String, Object> responseJson;
            try {
                responseJson = mapper.readValue(body, new TypeReference<>() {
                });
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return Mono.error(new SPRuntimeError(
                        "Issue while processing the request [ERR - 002]", HttpStatus.INTERNAL_SERVER_ERROR,txnId));
            }
            HandlerResponse hr = new HandlerResponse();
            responseJson.remove("applicationId");
            responseJson.remove("status");
            hr.setData(responseJson);
            hr.setTxnId(txnId);
            hr.setApplicationId(applicationId);
            return ServerResponse.ok().bodyValue(hr);
        });

    }
    public Mono<List<MvelDetailsDTO>> fetchMvelDetails(Integer serviceId) {

        Map<String, String> headers = Map.of();

        String url = METADATA_SERVICE.concat("apply/mvelDetails?");

        Mono<ResponseEntity<String>> call = AsynchronousApiExecutor.callExternalEndpoint(
                String.class,
                HttpMethod.POST,
                headers,
                Map.of("serviceId", serviceId),
                url,
                null,
                MediaType.APPLICATION_JSON
        );

        return call.flatMap(res -> {

            String body = res.getBody();

            Type type = new TypeToken<List<MvelDetailsDTO>>() {}.getType();

            List<MvelDetailsDTO> list =
                    (List<MvelDetailsDTO>) stringToEntityUsingType(body, type);

            return Mono.just(list);
        });
    }
    public Mono<MvelExecutionResponse> executeMvel(
            Long mvelId,
            String txnId,
            String activityId,
            String triggerPoint,
            String appId,
            Integer serviceId,
            String currentProcessId,
            String formData,
            Map<String, Object> appDetails,
            Map<String, Object> serviceDetails,
            Map<String, List<Integer>> userList,
            List<String> nextNodeList
    ) {

        String url = MVEL_EXECUTION_SERVICE.concat("execute");

        Map<String, String> headers = new HashMap<>();
        MvelExecutionRequest request = new MvelExecutionRequest();
        request.setFunctionId(mvelId);
        request.setTxnId(txnId);
        request.setActivityId(activityId);
        request.setTriggerPoint(triggerPoint);
        request.setApplicationId(appId);
        request.setServiceId(serviceId);
        request.setCurrentProcessId(currentProcessId);
        request.setFormData(formData);
        request.setApplicationDetails(appDetails);
        request.setServiceDetails(serviceDetails);
        request.setUserList(userList);
        request.setNextNodeList(nextNodeList);

        return AsynchronousApiExecutor.callExternalEndpoint(
                MvelExecutionResponse.class,
                HttpMethod.POST,
                headers,
                Map.of(),
                url,
                entityToString(request),
                MediaType.APPLICATION_JSON
        )
        .map(ResponseEntity::getBody)
        .flatMap(body -> {
            try {
                MvelExecutionResponse res =
                		mapper.readValue(body, MvelExecutionResponse.class);

                if (res == null) {
                    return Mono.error(new RuntimeException("MVEL response is null"));
                }

                return Mono.just(res);

            } catch (Exception e) {
                return Mono.error(new RuntimeException("Failed to parse MVEL response", e));
            }
        })
        .onErrorResume(ex -> {
            MvelExecutionResponse errorRes = new MvelExecutionResponse();
            errorRes.setSuccess(false);
            errorRes.setError(ex.getMessage());
            return Mono.just(errorRes);
        });
    }

    @SuppressWarnings("unchecked")
    public Mono<List<WorkflowInboxResponse>> fetchWFPInbox(UserSessionObject user) {

        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        String url = TRACKING_SERVICE.concat("workflow/inbox/list");

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
                                                                                    String.class,
                                                                                    HttpMethod.GET,
                                                                                    headers,
                                                                                    Collections.emptyMap(),
                                                                                    url,
                                                                                    null,
                                                                                    MediaType.APPLICATION_JSON
                                                                            );

        return callExternalEndpoint.flatMap(apiResponse -> {

            String body = apiResponse.getBody();

            if (body == null || body.isBlank()) {
                return Mono.error(new SPRuntimeError("Unable to fetch inbox", HttpStatus.FAILED_DEPENDENCY, null));
            }

            Type listType = new TypeToken<List<WorkflowInboxResponse>>() {}.getType();

            List<WorkflowInboxResponse> inboxList = (List<WorkflowInboxResponse>) stringToEntityUsingType(body, listType);
            return Mono.just(inboxList);
        });
    }
}

