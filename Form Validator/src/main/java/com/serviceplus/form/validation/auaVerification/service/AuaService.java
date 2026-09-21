package com.serviceplus.form.validation.auaVerification.service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import com.serviceplus.form.validation.auaVerification.dto.AuaRequest;
import com.serviceplus.form.validation.auaVerification.dto.AuaResponse;
import com.serviceplus.form.validation.auaVerification.engine.AuaEngine;
import com.serviceplus.form.validation.auaVerification.entity.AuaTransactionLog;
import com.serviceplus.form.validation.auaVerification.repository.AuaTransactionLogRepository;
import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.service.ReactiveApiClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class AuaService {

    private static final Logger log = LoggerFactory.getLogger("applicationFlowLogger");

    private static final String STATUS_INITIATED = "INITIATED";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final AuaTransactionLogRepository transactionLogRepository;
    private final AuaEngine auaEngine;
    private final ReactiveApiClient reactiveApiClient;
    private final ApplicationCryptoService applicationCryptoService;

    public AuaService(AuaTransactionLogRepository transactionLogRepository, AuaEngine auaEngine, ReactiveApiClient reactiveApiClient, ApplicationCryptoService applicationCryptoService) {
        this.transactionLogRepository = transactionLogRepository;
        this.auaEngine = auaEngine;
        this.reactiveApiClient = reactiveApiClient;
        this.applicationCryptoService = applicationCryptoService;
    }

    public Mono<AuaResponse> execute(AuaRequest request, UserSessionObject user) {

        validateRequest(request);

        String tenantId = user.getTenantId();

        log.info(
                "Starting AUA service execution. serviceId={}, taskId={}, txnId={}, operationType={}, attributeId={}, tenantId={}",
                request.getServiceId(),
                request.getTaskId(),
                request.getTxnId(),
                request.getOperationType(),
                request.getAttributeId(),
                tenantId
        );

        return reactiveApiClient
                .fetchServiceMetadata(
                        user,
                        request.getServiceId(),
                        request.getTxnId()
                )
                .doOnNext(metadata ->
                        log.info("Service metadata fetched for AUA. serviceId={}, txnId={}", request.getServiceId(), request.getTxnId())
                )
                .flatMap(metadata ->
                        prepareTransactionLog(request, tenantId)
                                .flatMap(logEntry -> {

                                    Map<String, Object> decryptedAttributes = decryptAttributes(request.getAttributes(), metadata);

                                    request.setAttributes(decryptedAttributes);

                                    initializeLog(logEntry, request, tenantId);

                                    return auaEngine
                                            .execute(
                                                    metadata,
                                                    request,
                                                    logEntry
                                            )
                                            .flatMap(execution -> {

                                                AuaResponse response = execution.getResponse();

                                                logEntry.setProviderId(execution.getProviderId());
                                                logEntry.setApiId(execution.getApiId());
                                                updateOperationLog(logEntry, request, response);

                                                return transactionLogRepository
                                                        .save(logEntry)
                                                        .doOnSuccess(saved ->
                                                                log.info(
                                                                        "AUA transaction log saved. serviceId={}, taskId={}, txnId={}, rowNo={}, operationType={}, attributeId={}, providerTxnId={}, status={}",
                                                                        request.getServiceId(),
                                                                        request.getTaskId(),
                                                                        request.getTxnId(),
                                                                        saved.getRowNo(),
                                                                        request.getOperationType(),
                                                                        request.getAttributeId(),
                                                                        saved.getProviderTxnId(),
                                                                        saved.getStatus()
                                                                )
                                                        )
                                                        .thenReturn(response);
                                            });
                                })
                )
                .doOnError(error ->
                        log.error(
                                "AUA service execution failed. serviceId={}, taskId={}, txnId={}, operationType={}, attributeId={}, error={}",
                                request.getServiceId(),
                                request.getTaskId(),
                                request.getTxnId(),
                                request.getOperationType(),
                                request.getAttributeId(),
                                error.getMessage(),
                                error
                        )
                );
    }


    private Mono<AuaTransactionLog> prepareTransactionLog(
            AuaRequest request,
            String tenantId) {

        return transactionLogRepository
                .findFirstByTxnIdAndServiceIdAndTenantIdAndOperationTypeAndAttributeIdAndRowNoOrderByIdDesc(
                        request.getTxnId(),
                        request.getServiceId(),
                        tenantId,
                        request.getOperationType(),
                        request.getAttributeId(),
                        request.getRowNo()
                )
                .map(existingLog -> {

                    AuaTransactionLog newLog = new AuaTransactionLog();
                    newLog.setNewEntity(true);

                    newLog.setRowNo(request.getRowNo());
                    newLog.setAttributeId(request.getAttributeId());
                    newLog.setOperationType(request.getOperationType());

                    return newLog;
                })
                .switchIfEmpty(
                        transactionLogRepository
                                .findFirstByTxnIdAndServiceIdAndTenantIdAndRowNoOrderByRowNoDesc(
                                        request.getTxnId(),
                                        request.getServiceId(),
                                        tenantId,
                                        request.getRowNo()
                                )
                                .map(lastLog -> {

                                    AuaTransactionLog newLog = new AuaTransactionLog();
                                    newLog.setNewEntity(true);

                                    newLog.setTxnId(request.getTxnId());
                                    newLog.setRowNo(lastLog.getRowNo());
                                    newLog.setAttributeId(request.getAttributeId());
                                    newLog.setProviderTxnId(lastLog.getProviderTxnId());
                                    newLog.setOperationType(request.getOperationType());

                                    return newLog;
                                })
                                .switchIfEmpty(
                                        Mono.fromSupplier(() -> {

                                            AuaTransactionLog newLog = new AuaTransactionLog();
                                            newLog.setNewEntity(true);

                                            newLog.setTxnId(request.getTxnId());
                                            newLog.setRowNo(request.getRowNo());
                                            newLog.setAttributeId(request.getAttributeId());
                                            newLog.setOperationType(request.getOperationType());

                                            return newLog;
                                        })
                                )
                );
    }

    private Integer nextRowNo(Integer currentRowNo) {

        if (currentRowNo == null || currentRowNo < 1) {
            return 1;
        }

        return currentRowNo + 1;
    }

    private Integer nextAttemptNo(Integer currentAttemptNo) {

        if (currentAttemptNo == null || currentAttemptNo < 1) {
            return 1;
        }

        return currentAttemptNo + 1;
    }

    private Map<String, Object> decryptAttributes(
            Map<String, Object> attributes,
            ServiceJSONDTO metadata) {

        Map<String, Object> decrypted = new HashMap<>();

        if (attributes == null || attributes.isEmpty()) {
            return decrypted;
        }

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {

            String attributeId = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                continue;
            }

            if (!(value instanceof String originalValue)) {
                decrypted.put(attributeId, value);
                continue;
            }

            if (originalValue.isBlank()) {
                decrypted.put(attributeId, originalValue);
                continue;
            }

            try {

                String plaintext =
                        applicationCryptoService.decrypt(originalValue);

                decrypted.put(attributeId, plaintext);

                log.info(
                        "AUA attribute decrypted successfully. attributeId={}",
                        attributeId
                );

            } catch (Exception e) {

                decrypted.put(attributeId, originalValue);

                log.info(
                        "AUA attribute is not encrypted or could not be decrypted. Using original value. attributeId={}",
                        attributeId
                );
            }
        }

        return decrypted;
    }

    private void initializeLog(
            AuaTransactionLog logEntry,
            AuaRequest request,
            String tenantId) {

        logEntry.setNewEntity(true);

        logEntry.setTxnId(request.getTxnId());

        logEntry.setServiceId(request.getServiceId());
        logEntry.setTaskId(request.getTaskId());
        logEntry.setTenantId(tenantId);
        logEntry.setAttributeId(request.getAttributeId());
        logEntry.setOperationType(request.getOperationType());
        logEntry.setStatus(STATUS_INITIATED);

        OffsetDateTime now = OffsetDateTime.now();

        if (logEntry.getCrDate() == null) {
            logEntry.setCrDate(now);
        }

        logEntry.setUpDate(now);

        log.info(
                "AUA transaction log initialized. serviceId={}, taskId={}, applicationTxnId={}, rowNo={}, attributeId={}, operationType={}",
                request.getServiceId(),
                request.getTaskId(),
                request.getTxnId(),
                logEntry.getRowNo(),
                request.getAttributeId(),
                request.getOperationType()
        );
    }

    private void updateOperationLog(
            AuaTransactionLog logEntry,
            AuaRequest request,
            AuaResponse response) {

        OffsetDateTime now = OffsetDateTime.now();

        if (response == null) {

            log.warn(
                    "AUA response is null. txnId={}, rowNo={}, attributeId={}",
                    request.getTxnId(),
                    logEntry.getRowNo(),
                    request.getAttributeId()
            );

            logEntry.setStatus(STATUS_FAILED);
            logEntry.setErrorCode(null);
            logEntry.setErrorMessage("AUA response is null");
            logEntry.setCompletedAt(now);
            logEntry.setUpDate(now);

            return;
        }

        if (response.isSuccess()) {

            logEntry.setStatus(STATUS_SUCCESS);

            log.info(
                    "AUA operation successful. operationType={}, txnId={},rowNo={}, attributeId={}, providerTxnId={}",
                    request.getOperationType(),
                    request.getTxnId(),
                    logEntry.getRowNo(),
                    request.getAttributeId(),
                    logEntry.getProviderTxnId()
            );

        } else {

            logEntry.setStatus(STATUS_FAILED);
            logEntry.setErrorCode(response.getErrorCode());

            log.warn(
                    "AUA operation failed. operationType={}, txnId={}, rowNo={},attributeId={}, providerTxnId={}, errorCode={}",
                    request.getOperationType(),
                    request.getTxnId(),
                    logEntry.getRowNo(),
                    request.getAttributeId(),
                    logEntry.getProviderTxnId(),
                    response.getErrorCode()
            );
        }

        logEntry.setCompletedAt(now);
        logEntry.setUpDate(now);
    }

    private void validateRequest(AuaRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("AUA request is required");
        }

        if (request.getServiceId() == null) {
            throw new IllegalArgumentException("serviceId is required");
        }

        if (request.getTxnId() == null || request.getTxnId().isBlank()) {
            throw new IllegalArgumentException("txnId is required");
        }

        if (request.getOperationType() == null || request.getOperationType().isBlank()) {
            throw new IllegalArgumentException("operationType is required");
        }
    }
}