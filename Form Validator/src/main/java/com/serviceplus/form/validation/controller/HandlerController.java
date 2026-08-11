package com.serviceplus.form.validation.controller;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.flow.EventRouter;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.service.PreProcessingService;
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

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_STATUS_KEY;
import static com.serviceplus.form.validation.utility.Utility.decryptServiceKeys;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

@RestController
public class HandlerController {

    private final  EventRouter applicationFlowRouter;

    private final  TransactionGeneration transactionGeneration;

    private final ApplicationFlowRouterRepository applicationFlowRouterRepository;

    private final PreProcessingService preProcessingService;

    public HandlerController(EventRouter applicationFlowRouter, TransactionGeneration transactionGeneration, ApplicationFlowRouterRepository applicationFlowRouterRepository, PreProcessingService preProcessingService) {
        this.applicationFlowRouter = applicationFlowRouter;
        this.transactionGeneration = transactionGeneration;
        this.applicationFlowRouterRepository = applicationFlowRouterRepository;
        this.preProcessingService = preProcessingService;
    }

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

    public Mono<ServerResponse> open(ServerRequest request) {

        Optional<String> appIdOpt = request.queryParam("appId");
        Optional<String> serviceKeyOpt = request.queryParam("serviceKey");
        Optional<String> serviceIdOpt = request.queryParam("serviceId");

        if (appIdOpt.isEmpty() || serviceKeyOpt.isEmpty() || serviceIdOpt.isEmpty()) {
            return Mono.error(new SPRuntimeError("Mandatory parameters is required", HttpStatus.BAD_REQUEST, null));
        }

        String appId = appIdOpt.get();
        String serviceKey = serviceKeyOpt.get();
        String serviceId = serviceIdOpt.get();

        ServiceMeta service = decryptServiceKeys(serviceKey);

        if (!service.getServiceId().toString().equals(serviceId)) {
            return Mono.error(new SPRuntimeError("Key mismatch", HttpStatus.NOT_ACCEPTABLE, null));
        }

        UserSessionObject user = requireNonNull(getUserSessionDetails(request.exchange().getRequest()));

        String taskId = service.getTaskId();

        return applicationFlowRouterRepository
                .findFirstByApplicationIdAndTaskIdAndServiceIdAndTenantIdAndActivityTypeOrderByIdDesc(
                        appId,
                        taskId,
                        service.getServiceId(),
                        user.getTenantId(),
                        ACTIVITY_FORM_STATUS_KEY)
                .flatMap(flow -> {

                    if (!isNull(flow.getCompleted())) {
                        applicationFlowLogs.info("Opening draft for application {} taskId {} txnId {}", appId, taskId, flow.getTxnId());
                        return draft(request);
                    }

                    applicationFlowLogs.info("No active draft found for application {} taskId {}. Opening new form.", appId, taskId);
                    return preProcessingService.apply(request);
                }
                ).switchIfEmpty(
                                preProcessingService.apply(request)
                );
    }



}
