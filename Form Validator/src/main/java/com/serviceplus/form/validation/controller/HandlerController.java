package com.serviceplus.form.validation.controller;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.flow.EventRouter;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import com.serviceplus.form.validation.service.DraftInitializationService;
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

import java.time.LocalDateTime;
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

    private final CurrentProcessRepository currentProcessRepository;

    private final DraftInitializationService draftInitializationService;

    public HandlerController(EventRouter applicationFlowRouter, TransactionGeneration transactionGeneration, ApplicationFlowRouterRepository applicationFlowRouterRepository, PreProcessingService preProcessingService, CurrentProcessRepository currentProcessRepository, DraftInitializationService draftInitializationService) {
        this.applicationFlowRouter = applicationFlowRouter;
        this.transactionGeneration = transactionGeneration;
        this.applicationFlowRouterRepository = applicationFlowRouterRepository;
        this.preProcessingService = preProcessingService;
        this.currentProcessRepository = currentProcessRepository;
        this.draftInitializationService = draftInitializationService;
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

        ServiceMeta serviceMeta = null;

        try{
            serviceMeta = decryptServiceKeys(serviceKey.get());
        }
        catch (Exception e){
            e.printStackTrace();
            return Mono.error(new SPRuntimeError("Invalid service key",HttpStatus.BAD_REQUEST,txnId.get()));
        }

        return  applicationFlowRouter.route(null, appIdOpt.orElse(null), request, txnId.orElse(null)
                                ,serviceMeta,false);

    }

    public Mono<ServerResponse> draft(ServerRequest request) {
        Optional<String> appIdOpt = request.queryParam("appId");
        Optional<String> serviceKey = request.queryParam("serviceKey");
        Optional<String> serviceIdOpt = request.queryParam("serviceId");
        Optional<String> txnIdOpt = request.queryParam("txnId");


        if(serviceKey.isEmpty() || appIdOpt.isEmpty() || serviceIdOpt.isEmpty()){
            return Mono.error(new SPRuntimeError("Mandatory parameters is required", HttpStatus.BAD_REQUEST,null));
        }

        ServiceMeta serviceMeta = decryptServiceKeys(serviceKey.get());

        if(!serviceMeta.getServiceId().toString().equals(serviceIdOpt.get())){
            return Mono.error(new SPRuntimeError("Key mismatch", HttpStatus.NOT_ACCEPTABLE,null));
        }

        return  applicationFlowRouter.route(null, appIdOpt.orElse(null), request, txnIdOpt.orElse(null)
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

        if (serviceKeyOpt.isEmpty() || serviceIdOpt.isEmpty()) {
            return Mono.error(new SPRuntimeError("Mandatory parameters are required", HttpStatus.BAD_REQUEST, null));
        }

        String appId = appIdOpt.orElse(null);

        if (appId == null || appId.isBlank()) {
            applicationFlowLogs.info("No applicationId provided. Calling preprocessing service");

            return preProcessingService.apply(request);
        }

        String serviceKey = serviceKeyOpt.get();
        String serviceId = serviceIdOpt.get();

        ServiceMeta service = decryptServiceKeys(serviceKey);

        if (service.getServiceId() == null || !service.getServiceId().toString().equals(serviceId)) {
            return Mono.error(new SPRuntimeError("Key mismatch", HttpStatus.NOT_ACCEPTABLE, null));
        }

        UserSessionObject user = requireNonNull(getUserSessionDetails(request.exchange().getRequest()));

        String tenantId = user.getTenantId();
        String taskId = service.getTaskId();

        return currentProcessRepository
                .existsByApplicationIdAndTenantId(appId, tenantId)
                .flatMap(applicationExists -> {

                    if (!applicationExists) {

                        applicationFlowLogs.info("Application {} does not exist in current process for tenant {}. Calling apply", appId, tenantId);
                        return preProcessingService.apply(request);
                    }

                    return currentProcessRepository
                            .findByApplicationIdAndCurrentTaskAndActionTaken(
                                    appId,
                                    taskId,
                                    "Y"
                            )
                            .flatMap(process -> {

                                applicationFlowLogs.info("Action already taken for application {} and task {}", appId, taskId);
                                return Mono.<ServerResponse>error(new SPRuntimeError("Action already taken", HttpStatus.CONFLICT, null));
                            })
                            .switchIfEmpty(
                                    currentProcessRepository
                                            .findByApplicationIdAndCurrentTaskAndActionTaken(
                                                    appId,
                                                    taskId,
                                                    "N"
                                            )
                                            .flatMap(currentProcess ->

                                                    applicationFlowRouterRepository
                                                            .findFirstByApplicationIdAndTaskIdAndServiceIdAndTenantIdAndActivityTypeAndLastUpdateAfterOrderByIdDesc(
                                                                    appId,
                                                                    taskId,
                                                                    service.getServiceId(),
                                                                    tenantId,
                                                                    ACTIVITY_FORM_STATUS_KEY,
                                                                    currentProcess.getInitiatedOn()
                                                            )
                                            )
                                            .flatMap(flow ->
                                                    draft(request)
                                            )
                                            .switchIfEmpty(
                                                    Mono.<ServerResponse>error(new SPRuntimeError("No active task found", HttpStatus.NOT_FOUND, null))
                                            )
                            );
                });
    }

    public Mono<ServerResponse> initializeDraft(ServerRequest request) {

        Optional<String> serviceKeyOpt = request.queryParam("serviceKey");
        Optional<String> serviceIdOpt = request.queryParam("serviceId");
        Optional<String> txnIdOpt = request.queryParam("txnId");
        Optional<String> appIdOpt = request.queryParam("appId");

        if (serviceKeyOpt.isEmpty() || serviceIdOpt.isEmpty() || txnIdOpt.isEmpty()) {
            return Mono.error(new SPRuntimeError("Mandatory parameters is required", HttpStatus.BAD_REQUEST, null));
        }

        ServiceMeta service;

        try {
            service = decryptServiceKeys(serviceKeyOpt.get());

        } catch (Exception e) {

            applicationFlowLogs.error("Invalid service key while initializing draft", e);
            return Mono.error(new SPRuntimeError("Invalid service key", HttpStatus.BAD_REQUEST, txnIdOpt.get()));
        }

        if (!service.getServiceId().toString().equals(serviceIdOpt.get())) {
            return Mono.error(new SPRuntimeError("Key mismatch", HttpStatus.NOT_ACCEPTABLE, txnIdOpt.get()));
        }

        UserSessionObject user = requireNonNull(getUserSessionDetails(request.exchange().getRequest()));

        String appId = appIdOpt.orElse(null);
        String txnId = txnIdOpt.get();

        return draftInitializationService
                .initializeDraft(
                        service,
                        user,
                        request.exchange().getRequest(),
                        appId,
                        txnId
                )
                .flatMap(handlerResponse -> {

                    applicationFlowLogs.info("Draft initialized successfully for application {} txnId {}", handlerResponse.getApplicationId(), handlerResponse.getTxnId());
                    return ServerResponse.ok().bodyValue(handlerResponse);
                })
                .onErrorResume(Exception.class, ex -> {

                    Throwable actual = Exceptions.unwrap(ex);
                    applicationFlowLogs.error("Error while initializing draft", ex);

                    return Mono.error(
                            actual instanceof SPRuntimeError spr
                                    ? spr
                                    : new SPRuntimeError(
                                    "Unable to initialize draft",
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    txnId
                            )
                    );
                });
    }


}
