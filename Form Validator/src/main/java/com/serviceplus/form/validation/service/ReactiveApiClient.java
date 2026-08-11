package com.serviceplus.form.validation.service;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.dto.OfficeDetailsDTO.OfficeUnitData;
import com.serviceplus.form.validation.executor.ApiExecutor;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.reflect.TypeToken;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;

import org.springframework.web.reactive.function.client.WebClient;
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

    @Value("${document.generation.service}")
    private String DOCUMENT_GENERATION_SERVICE;
    
    @Value("${mvel.execution.service}")
    private String MVEL_EXECUTION_SERVICE;

    @Value("${file.management.service}")
    private String FILE_MANAGEMENT_SERVICE;

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
        String url = FORM_MANAGEMENT_SERVICE.concat("getByFormIdApplicant?");
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
       return fetchServiceMetadata(user, serviceId, txnId)
               .flatMap(metadata -> {
                String body = metadata.getServiceAbbrevation();
                if (body == null) {
                    return Mono.error(new SPRuntimeError("Unable to process your request [SUB - 007]", HttpStatus.FAILED_DEPENDENCY,txnId));
                }
                return Mono.just(body);
            });
    }

	public Mono<ServiceJSONDTO> fetchServiceMetadata(UserSessionObject user,Integer serviceId,String txnId) {

	    final String REDIS_KEY = "ServiceMetaData_" + serviceId;

	    return redis.fetch(
	                    REDIS_KEY,
	                    new TypeToken<ServiceJSONDTO>() {}.getType())
	            .cast(ServiceJSONDTO.class)
	            .switchIfEmpty(fetchAndCacheServiceMetadata(user, serviceId, txnId));
	}
	
	private Mono<ServiceJSONDTO> fetchAndCacheServiceMetadata(
	        UserSessionObject user,
	        Integer serviceId,
			String txnId) {

		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

		String url = METADATA_SERVICE.concat("apply/serviceMetaData?");

		Mono<ResponseEntity<String>> response = AsynchronousApiExecutor.callExternalEndpoint(String.class,
				HttpMethod.POST, headers, Map.of("serviceId", serviceId), url, "", MediaType.APPLICATION_JSON);

		return response.flatMap(apiResponse -> {
			ServiceJSONDTO metadata = (ServiceJSONDTO) stringToEntityUsingType(apiResponse.getBody(),
					new TypeToken<ServiceJSONDTO>() {
					}.getType());

            applicationFlowLogs.info("Cache miss for service metadata for serviceId {} txnId {} , response {}",serviceId,txnId,entityToString(metadata));

			redis.add(metadata, "ServiceMetaData_" + serviceId, true, 5*60).subscribe();

			return Mono.just(Objects.requireNonNull(metadata));

		}).onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex, txnId));
	}
	
	public Mono<ServiceMeta> fetchServiceKey(Integer baseServiceId, UserSessionObject user, String appId, String taskId,
			Integer serviceId) {

		return fetchServiceMetadata(user, serviceId, "FETCH_SERVICE_KEY").map(metadata -> {

			ServiceMeta service = new ServiceMeta();

			service.setServiceId(metadata.getServiceId());
			service.setServiceName(metadata.getServiceName());
			service.setDepartmentName(metadata.getDepartmentName());
			service.setBaseServiceId(baseServiceId);

			if (taskId == null || taskId.isBlank()) {
				service.setTaskType(APPLICATION_SUBMISSION_TASK_FLAG);
				service.setTaskId(metadata.getApplSubmissionTaskId());
				service.setFormId(metadata.getApplFormId());
			} else {
				service.setTaskType(OFFICIAL_TASK_FLAG);
				service.setTaskId(taskId);
				service.setFormId(metadata.getTaskFormMapping().get(taskId));
			}

            List<WorkFlowDataDTO.WorkFlowAction> availableActions = metadata.getWorkFlowDetails()
                                                        .stream()
                                                        .filter(workflow ->
                                                                service.getTaskId().equals(workflow.getTaskId())
                                                        )
                                                        .filter(workflow ->
                                                                workflow.getAllowedAction() != null
                                                        )
                                                        .flatMap(workflow ->
                                                                workflow.getAllowedAction().stream()
                                                        )
                                                        .filter(Objects::nonNull)
                                                        .toList();

            service.setAvailableActions(availableActions);

			metadata.getActivityMap()
            .stream()
            .filter(a -> service.getTaskId().equals(a.getTaskId()))
            .findFirst()
            .ifPresent(activityMap -> {

                ActivityMapDTO taskActivity = new ActivityMapDTO();
                taskActivity.setData(activityMap.getData());

                service.setActivityMap(taskActivity);
                service.setServiceKey(encryptServiceKeys(service));
            });

            // Resolve Office Locations

            if(service.getTaskType().equals(APPLICATION_SUBMISSION_TASK_FLAG)) {

                metadata.getOfficeDetails()
                        .stream()
                        .filter(o -> service.getTaskId().equals(o.getTaskId()))
                        .findFirst()
                        .ifPresent(o -> {

                            List<ServiceMeta.AvailableApplyLocations> locations =
                                    o.getAllowedOffices()
                                            .stream()
                                            .map(office -> {
                                                ServiceMeta.AvailableApplyLocations location =
                                                        new ServiceMeta.AvailableApplyLocations();
                                                location.setOrgUnitCode(office.getOrgUnitCode() != null
                                                        ? office.getOrgUnitCode().longValue()
                                                        : null);
                                                location.setOrgUnitName(
                                                        office.getOrgUnitName());

                                                location.setHolderIds(new ArrayList<>());

                                                return location;

                                            })
                                            .collect(Collectors.toList());

                            service.setLocations(locations);
                        });
            }

			return service;
		});
	}
//    public Mono<ServiceMeta> fetchServiceKey(Integer baseServiceId, UserSessionObject user, String appId, String taskId, Integer serviceId) {
//        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
//        String url = METADATA_SERVICE.concat("apply/resolveForm?");
//        //CACHE AT THIS LEVEL ?????
//
//        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
//                                                                                String.class,
//                                                                                HttpMethod.POST,
//                                                                                headers,
//                                                                                Map.of("baseServiceId",baseServiceId,"taskId",taskId,"serviceId",serviceId),
//                                                                                url,
//                                                                                null,
//                                                                                MediaType.APPLICATION_JSON);
//
//
//        return callExternalEndpoint.flatMap(apiResponse -> {
//            String body = apiResponse.getBody();
//
//            Type listType = new TypeToken<ServiceMeta>() {}.getType();
//            ServiceMeta service = (ServiceMeta) stringToEntityUsingType(body, listType);
//            service.setServiceKey(encryptServiceKeys(service));
//            TaskActivity taskActivity = service.getActivityMap();
//            String key = SERVICE_ACTIVITY_REDIS_KEY_APPENDER.concat("_").concat(service.getServiceId().toString().concat("_").concat(service.getTaskId()));
//            redis.add(taskActivity,key,true,5).subscribe();
//            return Mono.just(service);
//            })
//            .onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex,"FROM FETCH SERVICE LIST"));
//
//    }

//    public Mono<ServiceWorkFlow> fetchProcessFlow(Integer baseServiceId, UserSessionObject user, String appId, String taskId, Integer serviceId,String txnId) {
//        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
//        String url = METADATA_SERVICE.concat("apply/processFlow?");
//
//        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
//                                                                String.class,
//                                                                HttpMethod.POST,
//                                                                headers,
//                                                                Map.of("serviceId",serviceId),
//                                                                url,
//                                                                null,
//                                                                MediaType.APPLICATION_JSON);
//
//
//        return callExternalEndpoint.flatMap(apiResponse -> {
//                    String body = apiResponse.getBody();
//
//                    Type listType = new TypeToken<ServiceWorkFlow>() {}.getType();
//                    ServiceWorkFlow workflow = (ServiceWorkFlow) stringToEntityUsingType(body, listType);
//                    final String REDIS_KEY =SERVICE_WORKFLOW_REDIS_KEY_APPENDER.concat("_").concat(baseServiceId.toString());
//                    redis.add(workflow,REDIS_KEY,true,5).subscribe();
//                    return Mono.just(workflow);
//                })
//                .onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex,txnId))
//                ;
//    }
	
	public Mono<ServiceJSONDTO> fetchProcessFlow(Integer baseServiceId, UserSessionObject user, String appId,
			String taskId, Integer serviceId, String txnId) {

		return fetchServiceMetadata(user, serviceId, txnId);
	}

    public Mono<HandlerResponse> fetchApplicantData(String dataId, String formId,UserSessionObject user,String txnId,String applicationId) {
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
            return Mono.just(hr);
        });

    }
    public Mono<List<MvelDetailsDTO>> fetchMvelDetails(UserSessionObject user, Integer serviceId, String txnId) {
        return fetchServiceMetadata(user, serviceId, txnId).flatMap(metadata -> {
            List<MvelDetailsDTO> list =metadata.getMvelDetailsDTO();
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
            Map<String, Map<String,List<String>>> taskLocationUserHolderMap,
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
        request.setTaskLocationUserHolderMap(taskLocationUserHolderMap);
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
    
	public Mono<List<WorkflowAssignmentDTO>> fetchWorkflowAssignments(Integer serviceId, String taskId,
			String locationId, UserSessionObject user, String txnId) {

		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

		String url = METADATA_SERVICE.concat("workflow/assignment-details?");

		Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(String.class,
				HttpMethod.GET, headers, Map.of("serviceId", serviceId, "taskId", taskId, "locationId", locationId),
				url, null, MediaType.APPLICATION_JSON);

		return callExternalEndpoint.flatMap(apiResponse -> {

			String body = apiResponse.getBody();

			Type listType = new TypeToken<List<WorkflowAssignmentDTO>>() {
			}.getType();

			List<WorkflowAssignmentDTO> assignments = (List<WorkflowAssignmentDTO>) stringToEntityUsingType(body,
					listType);

			return Mono.just(assignments);

		}).onErrorResume(WebClientResponseException.class, ex -> handleWebClientError(ex, txnId));
	}
	
	@SuppressWarnings("unchecked")
    public Mono<ServerSidePaginationRecord<WorkflowInboxResponse>> fetchWFPInbox(ServerHttpRequest request, UserSessionObject user) {

        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        String url = TRACKING_SERVICE.concat("/a/workflow/inbox/list");

        Map<String, Object> params = new HashMap<>();

        request.getQueryParams().forEach((key, value) -> {
            params.put(key, value.size() == 1 ? value.getFirst() : value);
        });

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
                                                                                    String.class,
                                                                                    HttpMethod.GET,
                                                                                    headers,
                                                                                    params,
                                                                                    url,
                                                                                    null,
                                                                                    MediaType.APPLICATION_JSON
                                                                            );

        return callExternalEndpoint.flatMap(apiResponse -> {

            String body = apiResponse.getBody();

            if (body == null || body.isBlank()) {
                return Mono.error(new SPRuntimeError("Unable to fetch inbox", HttpStatus.FAILED_DEPENDENCY, null));
            }

            Type listType = new TypeToken<ServerSidePaginationRecord<WorkflowInboxResponse>>() {}.getType();

            ServerSidePaginationRecord<WorkflowInboxResponse> inboxList = (ServerSidePaginationRecord<WorkflowInboxResponse>) stringToEntityUsingType(body, listType);
            return Mono.just(inboxList);
        });
    }

	public Mono<ServerSidePaginationRecord<WorkflowInboxResponse>> getInboxApplications(ServerHttpRequest request, UserSessionObject user) {

		Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

		String url = TRACKING_SERVICE.concat("a/inbox/applications");
		Map<String, Object> params = new HashMap<>();

		request.getQueryParams().forEach((key, value) -> {
			params.put(key, value.size() == 1 ? value.getFirst() : value);
		});

		Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(String.class,
				HttpMethod.GET, headers, params, url, null, MediaType.APPLICATION_JSON);

		return callExternalEndpoint.flatMap(apiResponse -> {

			String body = apiResponse.getBody();

			if (body == null || body.isBlank()) {
				return Mono.error(new SPRuntimeError("Unable to fetch inbox", HttpStatus.FAILED_DEPENDENCY, null));
			}
			Type listType = new TypeToken<ServerSidePaginationRecord<WorkflowInboxResponse>>() {}.getType();
			ServerSidePaginationRecord<WorkflowInboxResponse> inboxList = (ServerSidePaginationRecord<WorkflowInboxResponse>) stringToEntityUsingType(body, listType);
            return Mono.just(inboxList);
		});
	}

    public Mono<FetchTaskHolders> fetchTaskHolders(Integer serviceId,
                                                   String taskId,
                                                   UserSessionObject user,
                                                   String txnId) {

        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        String url = METADATA_SERVICE.concat("workflow/fetchTaskHolders?");

        Mono<ResponseEntity<String>> callExternalEndpoint =
                AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.POST,
                        headers,
                        Map.of("serviceId", serviceId, "taskId", taskId),
                        url,
                        null,
                        MediaType.APPLICATION_JSON);

        return callExternalEndpoint.flatMap(apiResponse -> {

            String body = apiResponse.getBody();

            FetchTaskHolders response = (FetchTaskHolders) stringToEntityUsingType(body, new TypeToken<FetchTaskHolders>() {}.getType());

            return Mono.just(response);

        }).onErrorResume(WebClientResponseException.class,
                ex -> handleWebClientError(ex, txnId));
    }

    public Mono<EscalationMvelResponse> executeEscalationMvel(
            EscalationMvelRequest request) {
        String url = MVEL_EXECUTION_SERVICE.concat("execute-escalation");
        Map<String, String> headers = new HashMap<>();
        return AsynchronousApiExecutor.callExternalEndpoint(
                EscalationMvelResponse.class,
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
                EscalationMvelResponse response =mapper.readValue(body, EscalationMvelResponse.class);
                if (response == null) {
                    return Mono.error(new RuntimeException("Escalation MVEL response is null"));
                }
                return Mono.just(response);
            } catch (Exception e) {
                return Mono.error(
                        new RuntimeException("Failed to parse Escalation MVEL response", e));
            }
        })
        .onErrorResume(ex -> {
            EscalationMvelResponse errorResponse = new EscalationMvelResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError(ex.getMessage());
            return Mono.just(errorResponse);
        });
    }

    public Mono<Map<String, Object>> fetchWorkflowAttributes(UserSessionObject user, String formId, String dataId,String txnId) {

        String url = FORM_MANAGEMENT_SERVICE.concat("b/workflow/attributes");

        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));
        Map<String, Object> request = Map.of("formId", formId, "dataId", dataId);

        Mono<ResponseEntity<String>> callExternalEndpoint = AsynchronousApiExecutor.callExternalEndpoint(
                                                                    String.class,
                                                                    HttpMethod.POST,
                                                                    headers,
                                                                    Collections.emptyMap(),
                                                                    url,
                                                                    entityToString(request),
                                                                    MediaType.APPLICATION_JSON);

        return callExternalEndpoint.flatMap(apiResponse -> {

            String body = apiResponse.getBody();

            if (body == null || body.isBlank()) {
                return Mono.error(new SPRuntimeError("Unable to fetch workflow attributes", HttpStatus.FAILED_DEPENDENCY, txnId));
            }

            try {

                Map<String, Object> response = mapper.readValue(body, new TypeReference<Map<String, Object>>() {
                });

                return Mono.just(response);

            } catch (JsonProcessingException e) {

                applicationFlowLogs.error("Failed to parse workflow attributes response", e);
                return Mono.error(new SPRuntimeError("Issue while processing workflow attributes response", HttpStatus.INTERNAL_SERVER_ERROR,txnId));
            }
        });
    }

    @SuppressWarnings("unchecked")
    public Mono<GenerateDocDResDTO> generateDocument(UserSessionObject user,
                                                     Integer serviceId,
                                                     Integer outputFormatId,
                                                     String applicationId,
                                                     boolean async,
                                                     String txnId) {

        String url = DOCUMENT_GENERATION_SERVICE.concat("doc/generate");

        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        Map<String, Object> request = new HashMap<>();
        request.put("serviceId", serviceId);
        request.put("outputFormatId", outputFormatId);
        request.put("applicationIdList", List.of(applicationId));
        request.put("async", async);

        return AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.POST,
                        headers,
                        Collections.emptyMap(),
                        url,
                        entityToString(request),
                        MediaType.APPLICATION_JSON)
                .flatMap(response -> {

                    List<GenerateDocDResDTO> result =
                            (List<GenerateDocDResDTO>) stringToEntityUsingType(response.getBody(), new TypeToken<List<GenerateDocDResDTO>>() {
                            }.getType());

                    if (result == null || result.isEmpty()) {
                        return Mono.error(new SPRuntimeError("Document generation failed.", HttpStatus.INTERNAL_SERVER_ERROR, txnId));
                    }

                    return Mono.just(result.getFirst());
                });
    }

    public Mono<FileViewResponse> getFileView(UserSessionObject user,
                                              List<String> uploadIds,
                                              String txnId) {

        String url = FILE_MANAGEMENT_SERVICE.concat("b/view/batch");

        Map<String, String> headers = Map.of(
                "USER-DETAILS", entityToString(user)
        );

        CommitRequest request = new CommitRequest();
        request.setUploadIds(uploadIds);

        return AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.POST,
                        headers,
                        Collections.emptyMap(),
                        url,
                        entityToString(request),
                        MediaType.APPLICATION_JSON)
                .flatMap(response -> {

                    FileViewResponse result =  (FileViewResponse) stringToEntityUsingType(
                                                        response.getBody(),
                                                        new TypeToken<FileViewResponse>() {}.getType());

                    return Mono.just(Objects.requireNonNull(result));
                });
    }

    public Mono<CreateUploadSessionsResponse> createUploadSession(UserSessionObject user, CreateUploadSessionsRequest request, String txnId) {

        String url = FILE_MANAGEMENT_SERVICE.concat("b/upload-sessions");

        Map<String, String> headers = Map.of("USER-DETAILS", entityToString(user));

        return AsynchronousApiExecutor.callExternalEndpoint(
                        String.class,
                        HttpMethod.POST,
                        headers,
                        Collections.emptyMap(),
                        url,
                        entityToString(request),
                        MediaType.APPLICATION_JSON)
                .flatMap(response -> {

                    CreateUploadSessionsResponse result =
                            (CreateUploadSessionsResponse) stringToEntityUsingType(
                                    response.getBody(),
                                    new TypeToken<CreateUploadSessionsResponse>() {}.getType());

                    return Mono.just(Objects.requireNonNull(result));
                });
    }

    public Mono<byte[]> downloadFromPresignedUrl(String previewUrl) {
        return WebClient.create()
                .get()
                .uri(previewUrl)
                .retrieve()
                .bodyToMono(byte[].class);
    }
}

