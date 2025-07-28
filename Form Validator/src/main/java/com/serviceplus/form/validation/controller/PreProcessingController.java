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
    	
    	if (!applyKeyOpt.isPresent()) {
            return Mono.error(new SPRuntimeError("applyKey is required", HttpStatus.BAD_REQUEST));
        }
    	
        return preProcessingService.apply(request.exchange().getRequest(), applyKeyOpt.get());
    }

    public Mono<ServerResponse> submitApplication(ServerRequest request) {
    	Optional<String> txnIdOpt = request.queryParam("txnId");
        Optional<String> applyKeyOpt = request.queryParam("applyKey");

        if (!txnIdOpt.isPresent()) {
            return Mono.error(new SPRuntimeError("txnId is required", HttpStatus.BAD_REQUEST));
        }

        if (!applyKeyOpt.isPresent()) {
            return Mono.error(new SPRuntimeError("applyKey is required", HttpStatus.BAD_REQUEST));
        }

        String txnId = txnIdOpt.get();
        String applyKey = applyKeyOpt.get();
        
    	return request.bodyToMono(String.class)
    	        .flatMap(appData -> 
    	            preProcessingService.applicationSubmission(
    	                request.exchange().getRequest(), txnId, appData, applyKey
    	            )
    	        );
    }
}
