package com.serviceplus.form.validation.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.service.PreProcessingService;

import reactor.core.publisher.Mono;

@Component
public class PreProcessingController {

    @Autowired
    private PreProcessingService preProcessingService;

    public Mono<ServerResponse> getServiceList(ServerRequest request) {
        return preProcessingService.serviceList(request.exchange().getRequest());
    }

    public Mono<ServerResponse> apply(ServerRequest request) {
    	Optional<String> applyKeyOpt = request.queryParam("applyKey");
    	
    	if (applyKeyOpt.isEmpty()) {
            return Mono.error(new SPRuntimeError("applyKey is required", HttpStatus.BAD_REQUEST));
        }
    	
        return preProcessingService.apply(request.exchange().getRequest(), applyKeyOpt.get());
    }

    public Mono<ServerResponse> fetchServiceKey(ServerRequest request){
        Optional<String> baseServiceIdOpt = request.queryParam("baseServiceId");
        Optional<String> applicationIdOpt = request.queryParam("applicationId");
        Optional<String> taskIdOpt = request.queryParam("taskId");
        Optional<String> serviceIdOpt = request.queryParam("serviceId");
        String serviceId = serviceIdOpt.orElse("-1");

        if (serviceIdOpt.isEmpty()) {
            return Mono.error(new SPRuntimeError("serviceId is required", HttpStatus.BAD_REQUEST));
        }

        return preProcessingService.fetchServiceKey(request.exchange().getRequest(),serviceIdOpt.get(),applicationIdOpt.orElse(""),taskIdOpt.orElse(""),serviceId);
    }
}
