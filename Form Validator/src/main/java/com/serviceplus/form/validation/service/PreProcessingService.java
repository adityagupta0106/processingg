package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.Services;
import com.serviceplus.form.validation.dto.UserSessionObject;

import reactor.core.publisher.Mono;

@Service
@SanitizeRequest
public class PreProcessingService {

    @Autowired
    private PreProcessingFacade preProcessingFacade;

    public Mono<ServerResponse> serviceList(ServerHttpRequest request) {
		try {
			UserSessionObject user = getUserSessionDetails(request);

			Mono<ServerResponse> map = preProcessingFacade.getServiceList(user)
														.flatMap(response -> ServerResponse.ok().bodyValue(response));

			return map;

		} catch (Exception e) {
			e.printStackTrace();
            return Mono.error(new SPRuntimeError("Internal Server Error",HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    public Mono<ServerResponse> apply(ServerHttpRequest request, String applyKey) {
        try {
            UserSessionObject user = getUserSessionDetails(request);
            //APPID,TASKID
            Services service = preProcessingFacade.decryptApplyKey(applyKey);

            return preProcessingFacade.getFormDataAndSaveTempTxn(service, user,request)
            									.flatMap(response -> ServerResponse.ok().bodyValue(response))
            									.onErrorResume(error -> {
            	                                    error.printStackTrace();
            	                                    return Mono.error(new SPRuntimeError(
            	                                        "Issue while processing the request ", HttpStatus.INTERNAL_SERVER_ERROR));
            	                                });

        } catch (Exception e) {
        	e.printStackTrace();
        	return Mono.error(new SPRuntimeError("Internal Server Error",HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    public Mono<ServerResponse> fetchServiceKey(ServerHttpRequest request, String baseServiceId, String appId, String taskId, String serviceId) {
        try {
            UserSessionObject user = getUserSessionDetails(request);

            return preProcessingFacade.fetchServiceKey(Integer.parseInt(baseServiceId), user,request,appId,taskId,Integer.parseInt(serviceId));

        } catch (Exception e) {
            e.printStackTrace();
            return Mono.error(new SPRuntimeError("Internal Server Error",HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

}


