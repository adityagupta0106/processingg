package com.serviceplus.form.validation.controller;

import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.dto.CallbackRequest;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.service.CallbackService;

import reactor.core.publisher.Mono;

@Component
public class CallbackController {

    private final CallbackService callbackService;

    public CallbackController(CallbackService callbackService) {
        this.callbackService = callbackService;
    }

    public Mono<ServerResponse> callback(ServerRequest request) {

        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());

        return request.bodyToMono(CallbackRequest.class)
                .flatMap(callbackRequest -> callbackService.callback(callbackRequest, user));
    }
}