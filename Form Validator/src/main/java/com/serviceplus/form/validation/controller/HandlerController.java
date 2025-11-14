package com.serviceplus.form.validation.controller;

import com.netflix.discovery.converters.Auto;
import com.serviceplus.form.validation.utility.ApplicationFlowRouter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Optional;

@RestController
public class HandlerController {

    @Autowired
    private ApplicationFlowRouter applicationFlowRouter;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    public Mono<ServerResponse> processHandler(ServerRequest request) {
        Optional<String> appIdOpt = request.queryParam("appId");
        Optional<String> txnId = request.queryParam("txnId");

        applicationFlowLogs.info("Handler called for txnId {} applicationId {}",txnId.orElse(null),appIdOpt.orElse(null));

        return  applicationFlowRouter.route(null, appIdOpt.orElse(null), request, txnId.orElse(null));

        //CHECK IF NEXT EXECUTION IS NEEDED OR SHOW RESULT

//        return route.flatMap(result -> {
//            result.
//        });

    }
}
