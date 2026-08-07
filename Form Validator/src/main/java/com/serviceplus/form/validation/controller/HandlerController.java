package com.serviceplus.form.validation.controller;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.flow.EventRouter;
import com.serviceplus.form.validation.service.TransactionGeneration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.serviceplus.form.validation.utility.Utility.decryptServiceKeys;

@RestController
public class HandlerController {

    @Autowired
    private EventRouter applicationFlowRouter;

    @Autowired
    private TransactionGeneration transactionGeneration;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    public Mono<ServerResponse> processAction(ServerRequest request) {
        Optional<String> appIdOpt = request.queryParam("appId");
        Optional<String> txnId = request.queryParam("txnId");
        Optional<String> serviceKey = request.queryParam("serviceKey");

        if(serviceKey.isEmpty() || txnId.isEmpty()){
            return Mono.error(new SPRuntimeError("Mandatory parameters is required", HttpStatus.BAD_REQUEST,""));
        }

        applicationFlowLogs.info("Handler called for txnId {} applicationId {}",txnId.orElse(null),appIdOpt.orElse(null));

        return  applicationFlowRouter.route(null, appIdOpt.orElse(null), request, txnId.orElse(null)
                                ,decryptServiceKeys(serviceKey.get()),false);

    }

    public Mono<ServerResponse> draft(ServerRequest request) {
        Optional<String> appIdOpt = request.queryParam("appId");
        Optional<String> serviceKey = request.queryParam("serviceKey");
        Optional<String> serviceIdOpt = request.queryParam("serviceId");
        Optional<String> txnIdOpt = request.queryParam("txnId");


        if(serviceKey.isEmpty() || appIdOpt.isEmpty() || serviceIdOpt.isEmpty() || txnIdOpt.isEmpty()){
            return Mono.error(new SPRuntimeError("Mandatory parameters is required", HttpStatus.BAD_REQUEST,null));
        }

        ServiceMeta serviceMeta = decryptServiceKeys(serviceKey.get());

        if(!serviceMeta.getServiceId().toString().equals(serviceIdOpt.get())){
            return Mono.error(new SPRuntimeError("Key mismatch", HttpStatus.NOT_ACCEPTABLE,null));
        }

        return  applicationFlowRouter.route(null, appIdOpt.orElse(null), request, txnIdOpt.get()
                ,decryptServiceKeys(serviceKey.get()),true);
    }

    public Mono<ServerResponse> edit(ServerRequest request) {
        Optional<String> appIdOpt = request.queryParam("appId");
        Optional<String> serviceKey = request.queryParam("serviceKey");
        Optional<String> txnIdOpt = request.queryParam("txnId");

        if (serviceKey.isEmpty() || appIdOpt.isEmpty() || txnIdOpt.isEmpty()) {
            return Mono.error(new SPRuntimeError("Mandatory parameters is required", HttpStatus.BAD_REQUEST,null));
        }

        ServiceMeta service = decryptServiceKeys(serviceKey.get());

        applicationFlowLogs.info("Edit API called for application {} txnId {}",appIdOpt.get(),txnIdOpt.get());

        return transactionGeneration.editApplication(
                        service, appIdOpt.get(),txnIdOpt.get(), request.exchange().getRequest()
                )
                .flatMap(processingTxn ->
                        applicationFlowRouter.route(null, appIdOpt.get(), request, processingTxn.getTxnId()
                                , service, true)
                )
                .onErrorResume(Exception.class, ex -> {

                    Throwable actual = Exceptions.unwrap(ex);
                    applicationFlowLogs.error("Error occurred for txnId {} applicationId {} is {}", txnIdOpt.get(), appIdOpt.get(), ex);

                    return Mono.error(
                            actual instanceof SPRuntimeError spr
                                    ? spr
                                    : new SPRuntimeError("Edit error [EDIT - 02]",
                                    HttpStatus.INTERNAL_SERVER_ERROR,txnIdOpt.get())
                    );
                });
    }

}
