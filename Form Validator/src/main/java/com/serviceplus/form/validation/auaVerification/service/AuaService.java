package com.serviceplus.form.validation.auaVerification.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.serviceplus.form.validation.auaVerification.dto.AuaApiConfigurationDTO;
import com.serviceplus.form.validation.auaVerification.dto.AuaRequest;
import com.serviceplus.form.validation.auaVerification.dto.AuaResponse;
import com.serviceplus.form.validation.auaVerification.engine.AuaEngine;
import com.serviceplus.form.validation.auaVerification.entity.AuaTransactionLog;
import com.serviceplus.form.validation.auaVerification.enums.AuaOperationType;
import com.serviceplus.form.validation.auaVerification.repository.AuaTransactionLogRepository;
import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.service.ReactiveApiClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

import static java.util.Objects.isNull;

@Service
public class AuaService {

    private static final Logger log = LoggerFactory.getLogger("applicationFlowLogger");

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

        log.info("Starting AUA service execution. serviceId={}, taskId={}, txnId={}, operationType={}, tenantId={}",
                request.getServiceId(),
                request.getTaskId(),
                request.getTxnId(),
                request.getOperationType(),
                tenantId
        );

        return reactiveApiClient.fetchServiceMetadata(user, request.getServiceId(), request.getTxnId())

                .doOnNext(metadata -> log.info("Service metadata fetched for AUA. serviceId={}, txnId={}",
                        request.getServiceId(),
                        request.getTxnId())
                )
                .flatMap(metadata ->

                        transactionLogRepository.findByTxnIdAndAttributeIdAndServiceIdAndTenantIdAndOperationType(
                                        request.getTxnId(),
                                        request.getAttributeId(),
                                        request.getServiceId(),
                                        tenantId,
                                        request.getOperationType()
                                )

                                .defaultIfEmpty(new AuaTransactionLog())
                                .flatMap(logEntry -> {

                                    Map<String, Object> decryptedAttributes = decryptAttributes(request.getAttributes(),metadata);
                                    request.setAttributes(decryptedAttributes);

                                    initializeLog(logEntry, request, tenantId);

                                    return auaEngine.execute(metadata, request,logEntry)

                                            .flatMap(execution -> {

                                                AuaResponse response = execution.getResponse();

                                                logEntry.setProviderId(execution.getProviderId());
                                                logEntry.setApiId(execution.getApiId());

                                                updateOperationLog(logEntry, request, response);

                                                return transactionLogRepository.save(logEntry)

                                                        .doOnSuccess(saved ->
                                                                log.info("AUA transaction log saved. serviceId={}, taskId={}, txnId={}, operationType={}, success={}",
                                                                        request.getServiceId(),
                                                                        request.getTaskId(),
                                                                        request.getTxnId(),
                                                                        request.getOperationType(),
                                                                        response.isSuccess())
                                                        )
                                                        .thenReturn(response);
                                            });
                                }))

                .doOnError(error ->
                        log.error("AUA service execution failed. serviceId={}, taskId={}, txnId={}, operationType={}, error={}",
                                request.getServiceId(),
                                request.getTaskId(),
                                request.getTxnId(),
                                request.getOperationType(),
                                error.getMessage(), error)
                );
    }

    private Map<String, Object> decryptAttributes(Map<String, Object> attributes, ServiceJSONDTO metadata) {

        Map<String, Object> decrypted = new HashMap<>();

        if (attributes == null || attributes.isEmpty()) {
            return decrypted;
        }

        if (metadata == null || metadata.getAuaApiConfigurations() == null) {
            throw new IllegalArgumentException("AUA metadata not available");
        }

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {

            String attributeId = entry.getKey();

            Object value = entry.getValue();

            if (value == null) {
                continue;
            }

            boolean sensitive = isSensitiveAttribute(metadata, attributeId);

            if (sensitive) {

                log.debug("Sensitive AUA attribute received. attributeId={}", attributeId);

                if (!(value instanceof String)) {
                    throw new IllegalArgumentException("Encrypted value must be a string for sensitive attribute: " + attributeId);
                }

                String encryptedValue = String.valueOf(value);

                if (encryptedValue.isBlank()) {
                    throw new IllegalArgumentException("Encrypted value is empty for sensitive attribute: " + attributeId);
                }

                try {

                    String plaintext = applicationCryptoService.decrypt(encryptedValue);

                    decrypted.put(attributeId, plaintext);

                    log.debug("Sensitive AUA attribute decrypted successfully. attributeId={}", attributeId);

                } catch (Exception e) {

                    log.error("Failed to decrypt sensitive AUA attribute. attributeId={}", attributeId, e);
                    throw new IllegalArgumentException("Invalid encrypted value for attribute: " + attributeId, e);
                }

            }

            else {

                decrypted.put(attributeId, value);

                log.debug("Non-sensitive AUA attribute accepted. attributeId={}", attributeId);
            }
        }

        return decrypted;
    }

    private boolean isSensitiveAttribute(ServiceJSONDTO metadata, String attributeId) {

        if (attributeId == null || attributeId.isBlank()) {
            return false;
        }

        for (AuaApiConfigurationDTO api : metadata.getAuaApiConfigurations()) {

            if (api == null || api.getMessages() == null) {
                continue;
            }

            for (AuaApiConfigurationDTO.AuaApiMessageDTO message : api.getMessages()) {

                if (message == null || message.getFields() == null) {
                    continue;
                }

                for (AuaApiConfigurationDTO.AuaApiMessageDTO.AuaApiFieldDTO field : message.getFields()) {

                    if (field == null) {
                        continue;
                    }

                    if (attributeId.equalsIgnoreCase(field.getSourcePath())) {
                        return Boolean.TRUE.equals(field.getSensitive());
                    }
                }
            }
        }

        return false;
    }

    private void initializeLog(AuaTransactionLog logEntry, AuaRequest request, String tenantId) {

        if (logEntry.getId() == null) {
            logEntry.setNewEntity(Boolean.TRUE);
        }

        logEntry.setTxnId(request.getTxnId());
        logEntry.setServiceId(request.getServiceId());
        logEntry.setTaskId(request.getTaskId());
        logEntry.setTenantId(tenantId);
        logEntry.setAttributeId(request.getAttributeId());

        logEntry.setOperationType(request.getOperationType());

        logEntry.setStatus("INITIATED");

        if (logEntry.getId() == null) {

            logEntry.setProviderId(null);
            logEntry.setApiId(null);
            logEntry.setProviderTxnId(null);
            logEntry.setErrorCode(null);
            logEntry.setErrorMessage(null);
        }

        LocalDateTime now = LocalDateTime.now();

        if (logEntry.getCrDate() == null) {
            logEntry.setCrDate(now);
        }

        logEntry.setRequestedAt(now);
        logEntry.setUpDate(now);

        log.info("AUA transaction log initialized. serviceId={}, taskId={}, txnId={}, attributeId={}, operationType={}",
                request.getServiceId(),
                request.getTaskId(),
                request.getTxnId(),
                request.getAttributeId(),
                request.getOperationType()
        );
    }

    private void updateOperationLog(AuaTransactionLog logEntry, AuaRequest request, AuaResponse response) {

        if (response == null) {

            log.warn("AUA response is null. txnId={}, attributeId={}", request.getTxnId(), request.getAttributeId());

            logEntry.setStatus("FAILED");
            logEntry.setCompletedAt(LocalDateTime.now());
            logEntry.setUpDate(LocalDateTime.now());

            return;
        }

        String operation = request.getOperationType();

        logEntry.setOperationType(operation);

        if (response.isSuccess()) {

            logEntry.setStatus("SUCCESS");
            log.info("AUA operation successful. operationType={}, txnId={}, attributeId={}", operation, request.getTxnId(), request.getAttributeId());

        } else {

            logEntry.setStatus("FAILED");
            logEntry.setErrorCode(response.getErrorCode());

            log.warn(
                    "AUA operation failed. operationType={}, txnId={}, attributeId={}, errorCode={}",
                    operation,
                    request.getTxnId(),
                    request.getAttributeId(),
                    response.getErrorCode()
            );
        }

        logEntry.setProviderTxnId(response.getTransactionId());

        logEntry.setCompletedAt(LocalDateTime.now());

        logEntry.setUpDate(LocalDateTime.now());
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