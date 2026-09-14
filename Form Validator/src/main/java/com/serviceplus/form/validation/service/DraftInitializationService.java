package com.serviceplus.form.validation.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.HandlerResponse;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.Collections;
import java.util.Map;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_STATUS_KEY;
import static com.serviceplus.form.validation.utility.Utility.entityToString;
import static com.serviceplus.form.validation.utility.Utility.isEmpty;

@Service
public class DraftInitializationService {

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    private final TempTransactionLogService tempTransactionLogService;
    private final PreProcessingFacade preProcessingFacade;
    private final ApplicationFlowRouterRepository applicationFlowRouterRepository;
    private final TransactionGeneration transactionGeneration;
    private final ReactiveApiClient reactiveApiClient;
    private final ObjectMapper objectMapper;

    public DraftInitializationService(TempTransactionLogService tempTransactionLogService, PreProcessingFacade preProcessingFacade, ApplicationFlowRouterRepository applicationFlowRouterRepository, TransactionGeneration transactionGeneration, ReactiveApiClient reactiveApiClient, ObjectMapper objectMapper) {
        this.tempTransactionLogService = tempTransactionLogService;
        this.preProcessingFacade = preProcessingFacade;
        this.applicationFlowRouterRepository = applicationFlowRouterRepository;
        this.transactionGeneration = transactionGeneration;
        this.reactiveApiClient = reactiveApiClient;
        this.objectMapper = objectMapper;
    }

    public Mono<HandlerResponse> initializeDraft(
            ServiceMeta service,
            UserSessionObject user,
            ServerHttpRequest request,
            String appId,
            String txnId
    ) {

        return tempTransactionLogService
                .fetch(txnId)
                .flatMap(tempLog -> {

                    applicationFlowLogs.info(
                            "Initializing draft from Redis for applicationId {} " +
                                    "serviceId {} taskId {} txnId {}",
                            appId,
                            service.getServiceId(),
                            service.getTaskId(),
                            txnId
                    );

                    return initializeDraftFromTempTransaction(
                            service,
                            user,
                            request,
                            appId,
                            tempLog
                    );
                })

                .switchIfEmpty(
                        initializeDraftFromDatabase(
                                service,
                                user,
                                request,
                                appId,
                                txnId
                        )
                )

                .onErrorResume(Exception.class, ex -> {

                    Throwable actual = Exceptions.unwrap(ex);

                    if (actual instanceof SPRuntimeError spr) {
                        return Mono.error(spr);
                    }

                    applicationFlowLogs.error(
                            "Error while initializing draft for applicationId {} " +
                                    "serviceId {} taskId {} txnId {}",
                            appId,
                            service.getServiceId(),
                            service.getTaskId(),
                            txnId,
                            ex
                    );

                    return Mono.error(
                            new SPRuntimeError(
                                    "Unable to initialize draft [DRF - 01]",
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    txnId
                            )
                    );
                });
    }

    private Mono<HandlerResponse> initializeDraftFromTempTransaction(ServiceMeta service, UserSessionObject user, ServerHttpRequest request, String appId, TempTransactionLogs tempLog) {

        return reactiveApiClient
                .fetchReferenceAbbrviation(
                        service.getServiceId(),
                        user,
                        tempLog.getTxnId()
                )
                .flatMap(abbr -> {

                    String referenceNo = abbr.concat("/").concat(String.valueOf(Year.now().getValue())).concat("/").concat(tempLog.getTxnId());

                    applicationFlowLogs.info(
                            "Initializing draft from Redis for applicationId {} txnId {} referenceNo {}",
                            appId,
                            tempLog.getTxnId(),
                            referenceNo
                    );

                    return preProcessingFacade
                            .getFormDataAndSaveTxn(
                                    service,
                                    user,
                                    request,
                                    tempLog,
                                    appId,
                                    null,
                                    ACTIVITY_FORM_STATUS_KEY,
                                    Boolean.TRUE,
                                    null,
                                    referenceNo
                            )
                            .flatMap(processingTxn ->
                                    saveDraftForm(
                                            processingTxn, service, user, null, processingTxn.getApplicationId(),request
                                    )
                                    .thenReturn(
                                            buildDraftResponse(processingTxn, service)
                                    )
                            );
                });
    }


    private Mono<HandlerResponse> initializeDraftFromDatabase(
            ServiceMeta service,
            UserSessionObject user,
            ServerHttpRequest request,
            String appId,
            String txnId
    ) {

        if (isEmpty(appId)) {
            return Mono.error(new SPRuntimeError("ApplicationId is required when transaction is not available", HttpStatus.BAD_REQUEST, txnId));
        }

        return applicationFlowRouterRepository
                .findByApplicationIdAndTxnIdAndCompletedAndTaskIdAndServiceIdAndTenantId(
                        appId,
                        txnId,
                        0,
                        service.getTaskId(),
                        service.getServiceId(),
                        user.getTenantId()
                )
                .switchIfEmpty(
                        Mono.error(new SPRuntimeError("Invalid Form Request [DRF - 02]", HttpStatus.BAD_REQUEST, txnId))
                )
                .flatMap(flow -> {

                    applicationFlowLogs.info(
                            "Initializing draft from database for " +
                                    "applicationId {} txnId {} dataId {}",
                            flow.getApplicationId(),
                            flow.getTxnId(),
                            flow.getDataId()
                    );

                    return initializeDraftFromFlow(
                            service,
                            user,
                            request,
                            flow
                    );
                });
    }

    private Mono<HandlerResponse> initializeDraftFromFlow(ServiceMeta service, UserSessionObject user, ServerHttpRequest request, ApplicationFlowStatusEntity flow) {

        String applicationId = flow.getApplicationId();
        String dataId = flow.getDataId();

        applicationFlowLogs.info(
                "Initializing draft from DB applicationId {} txnId {} dataId {} formId {}",
                applicationId,
                flow.getTxnId(),
                dataId,
                flow.getFormId()
        );

        TempTransactionLogs tempTransactionLog = new TempTransactionLogs();
        tempTransactionLog.setTxnId(flow.getTxnId());

        return preProcessingFacade
                .getFormDataAndSaveTxn(
                        service,
                        user,
                        request,
                        tempTransactionLog,
                        applicationId,
                        dataId,
                        ACTIVITY_FORM_STATUS_KEY,
                        Boolean.FALSE,
                        flow,
                        null
                )
                .flatMap(processingTxn ->
                        saveDraftForm(
                                processingTxn, service, user, dataId, applicationId,request
                        ).thenReturn(
                                buildDraftResponse(processingTxn, service)
                        )
                );
    }

    private Mono<ResponseEntity<String>> saveDraftForm(ProcessingTxn processingTxn, ServiceMeta service, UserSessionObject user, String dataId, String applicationId, ServerHttpRequest request) {

        applicationFlowLogs.info("Calling Form Management API for draft applicationId {} txnId {} dataId {}", applicationId, processingTxn.getTxnId(), dataId);

        return request.getBody()
                .reduce(new StringBuilder(), (body, dataBuffer) -> {

                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);

                    body.append(
                            new String(bytes, StandardCharsets.UTF_8)
                    );

                    DataBufferUtils.release(dataBuffer);

                    return body;
                })
                .map(StringBuilder::toString)
                .flatMap(formData ->
                        reactiveApiClient.saveFormData(
                                processingTxn.getTxnId(),
                                service,
                                formData,
                                user,
                                dataId,
                                applicationId,
                                Boolean.TRUE
                        )
                )
                .flatMap(response ->
                        updateFlowDataId(
                                applicationId,
                                processingTxn,
                                service,
                                user,
                                response.getBody()
                        )
                        .thenReturn(response)
                );
    }

    private Mono<Void> updateFlowDataId(String appId, ProcessingTxn txnLog, ServiceMeta service, UserSessionObject user, String responseBody) {

        Map<String, Object> responseJson;

        applicationFlowLogs.info("Response from form management for txnId {} applicationId {} response {}", txnLog.getTxnId(), appId, responseBody);

        try {
            responseJson = objectMapper.readValue(responseBody, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {

            applicationFlowLogs.error("Error parsing Form Management response for txnId {}", txnLog.getTxnId(), e);
            return Mono.error(new SPRuntimeError("Invalid downstream response [SUB-003]", HttpStatus.BAD_GATEWAY, txnLog.getTxnId()));
        }

        String dataId = String.valueOf(responseJson.getOrDefault("dataId", ""));

        if (isEmpty(dataId)) {
            applicationFlowLogs.error("DataId missing in Form Management response for txnId {}", txnLog.getTxnId());
            return Mono.error(new SPRuntimeError("Unable to process request [01]", HttpStatus.BAD_GATEWAY, txnLog.getTxnId()));
        }

        return applicationFlowRouterRepository
                .findByApplicationIdAndTxnIdAndCompletedAndTaskIdAndServiceIdAndTenantId(
                        appId,
                        txnLog.getTxnId(),
                        0,
                        service.getTaskId(),
                        service.getServiceId(),
                        user.getTenantId()
                )
                .switchIfEmpty(
                        Mono.error(new SPRuntimeError("Application flow not found after saving draft", HttpStatus.BAD_REQUEST, txnLog.getTxnId()))
                )
                .flatMap(flowStatus -> {

                    flowStatus.setDataId(dataId);

                    applicationFlowLogs.info(
                            "Updating dataId {} for applicationId {} txnId {}",
                            dataId,
                            appId,
                            txnLog.getTxnId()
                    );

                    return applicationFlowRouterRepository.save(flowStatus);
                })
                .then();
    }

    private HandlerResponse buildDraftResponse(ProcessingTxn processingTxn, ServiceMeta service) {

        HandlerResponse response = new HandlerResponse();

        response.setTxnId(processingTxn.getTxnId());
        response.setApplicationId(processingTxn.getApplicationId());
        response.setActivityType(ACTIVITY_FORM_STATUS_KEY);
        response.setActivityEnd(Boolean.FALSE);

        return response;
    }
}
