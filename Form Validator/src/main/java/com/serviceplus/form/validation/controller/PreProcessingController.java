package com.serviceplus.form.validation.controller;

import java.util.Optional;

import com.serviceplus.form.validation.dto.ServiceMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.service.PreProcessingService;

import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.Utility.isEmpty;

@Component
public class PreProcessingController {

    @Autowired
    private PreProcessingService preProcessingService;

    public Mono<ServerResponse> getServiceList(ServerRequest request) {
        return preProcessingService.serviceList(request.exchange().getRequest());
    }

    public Mono<ServerResponse> render(ServerRequest request) {

      // return fetchServiceKey(request).flatMap(res -> {
            //String sKey = res.getServiceKey();
            Optional<String> applyKeyOpt = request.queryParam("serviceKey");
            Optional<String> serviceIdOpt = request.queryParam("serviceId");

            if (applyKeyOpt.isEmpty() || serviceIdOpt.isEmpty()) {
                return Mono.error(new SPRuntimeError("Parameters missing", HttpStatus.BAD_REQUEST,null));
            }

            return preProcessingService.apply(request.exchange().getRequest(), applyKeyOpt.get(),serviceIdOpt.get());
       // });


    }

    public Mono<ServerResponse> fetchServiceKey(ServerRequest request){
        Optional<String> baseServiceIdOpt = request.queryParam("baseServiceId");
        Optional<String> applicationIdOpt = request.queryParam("applicationId");
        Optional<String> taskIdOpt = request.queryParam("taskId");
        Optional<String> serviceIdOpt = request.queryParam("serviceId");
        String serviceId = serviceIdOpt.orElse("-1");

        if (serviceIdOpt.isEmpty()) {
            return Mono.error(new SPRuntimeError("serviceId is required", HttpStatus.BAD_REQUEST,null));
        }

        return preProcessingService.fetchServiceKey(request.exchange().getRequest(),serviceIdOpt.get(),applicationIdOpt.orElse(""),taskIdOpt.orElse(""),serviceId);
    }
}
