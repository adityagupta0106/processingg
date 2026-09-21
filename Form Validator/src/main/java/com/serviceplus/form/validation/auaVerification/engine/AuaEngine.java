package com.serviceplus.form.validation.auaVerification.engine;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.auaVerification.dto.AuaApiConfigurationDTO;
import com.serviceplus.form.validation.auaVerification.dto.AuaExecutionResult;
import com.serviceplus.form.validation.auaVerification.dto.AuaRequest;
import com.serviceplus.form.validation.auaVerification.dto.AuaResponse;
import com.serviceplus.form.validation.auaVerification.entity.AuaTransactionLog;
import com.serviceplus.form.validation.dto.IntegrationRequest;
import com.serviceplus.form.validation.dto.IntegrationResponse;
import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.esb.EsbClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class AuaEngine {

    private static final Logger log = LoggerFactory.getLogger("applicationFlowLogger");

    private final AuaRequestBuilder requestBuilder;
    private final EsbClient esbClient;
    private final AuaResponseParser responseParser;
    private final AuaXmlLogger auaXmlLogger;

    public AuaEngine(AuaRequestBuilder requestBuilder, EsbClient esbClient, AuaResponseParser responseParser, AuaXmlLogger auaXmlLogger) {
        this.requestBuilder = requestBuilder;
        this.esbClient = esbClient;
        this.responseParser = responseParser;
        this.auaXmlLogger = auaXmlLogger;
    }

    public Mono<AuaExecutionResult> execute(ServiceJSONDTO metadata, AuaRequest request, AuaTransactionLog logEntry) {

        if (request == null) {
            return Mono.error(new IllegalArgumentException("AUA request is required"));
        }

        log.info("Starting AUA execution. serviceId={}, taskId={}, txnId={}, operationType={}",
                request.getServiceId(),
                request.getTaskId(),
                request.getTxnId(),
                request.getOperationType());

        AuaApiConfigurationDTO api;

        try {
            api = findApi(metadata, request.getOperationType());
        } catch (Exception e) {
            log.error("Failed to find Api definition. serviceId={}, taskId={}, txnId={}, operationType={}, error={}",
                    request.getServiceId(),
                    request.getTaskId(),
                    request.getTxnId(),
                    request.getOperationType(), e.getMessage(), e);

            return Mono.error(new SPRuntimeError("Unable to process the request [A - 01]", HttpStatus.INTERNAL_SERVER_ERROR,request.getTxnId()));
        }

        log.info("AUA API selected. operationType={}, apiType={}, endpoint={}", request.getOperationType(), api.getApiType(), api.getEndpoint());

        String xml;

        try {

            xml = requestBuilder.buildRequest(api, request.getAttributes(), logEntry);

            String requestLogXml = auaXmlLogger.prepareForLogging(xml, api.getRequestPayload());

            log.info("AUA request. apiType={}, xml={}", api.getApiType(), requestLogXml);

        } catch (Exception e) {

            log.error("Failed to build AUA request. serviceId={}, taskId={}, txnId={}, operationType={}, error={}",
                    request.getServiceId(),
                    request.getTaskId(),
                    request.getTxnId(),
                    request.getOperationType(), e.getMessage(), e);

            return Mono.error(new SPRuntimeError("Unable to process the request [A - 02]", HttpStatus.INTERNAL_SERVER_ERROR,request.getTxnId()));
        }

        log.info("AUA request XML generated. operationType={}, apiType={}, txnId={}",
                request.getOperationType(),
                api.getApiType(),
                request.getTxnId());

        log.info("AUA request XML generated. apiType={}, xmlLength={}", api.getApiType(), xml != null ? xml.length() : 0);

        IntegrationRequest integrationRequest;

        try {

            integrationRequest = buildIntegrationRequest(api, xml);

        } catch (Exception e) {

            log.error("Failed to create ESB integration request. apiType={}, endpoint={}", api.getApiType(), api.getEndpoint(), e);
            return Mono.error(new SPRuntimeError("Unable to process the request [A - 03]", HttpStatus.INTERNAL_SERVER_ERROR,request.getTxnId()));
        }

        log.info("Invoking AUA provider through ESB. operationType={}, apiType={}, endpoint={}", request.getOperationType(), api.getApiType(), api.getEndpoint());

        return esbClient.invoke(integrationRequest)

                .map(integrationResponse -> {

                    log.info("Received ESB response. operationType={}, apiType={}, success={}, statusCode={}",
                            request.getOperationType(),
                            api.getApiType(),
                            integrationResponse != null && integrationResponse.isSuccess(),
                            integrationResponse != null ? integrationResponse.getStatusCode() : null);

                    validateIntegrationResponse(integrationResponse);

                    String providerResponse = extractResponseBody(integrationResponse);

                    log.info("Provider response received. operationType={}, apiType={}, txnId={}, responseLength={}",
                            request.getOperationType(),
                            api.getApiType(),
                            request.getTxnId(),
                            providerResponse != null ? providerResponse.length() : 0);

                    String requestLogXml = auaXmlLogger.prepareForLogging(providerResponse, api.getResponsePayload());

                    log.info("AUA response. apiType={}, xml={}", api.getApiType(), requestLogXml);

                    AuaResponse auaResponse = responseParser.parse(api, providerResponse);

                    log.info(
                            "AUA response parsed. operationType={}, apiType={}, txnId={}, success={}, providerTransactionIdPresent={}, errorCodePresent={}",
                            request.getOperationType(),
                            api.getApiType(),
                            request.getTxnId(),
                            auaResponse.isSuccess(),
                            auaResponse.getTransactionId() != null,
                            auaResponse.getErrorCode() != null
                                    && !auaResponse.getErrorCode().isBlank()
                    );

                    return new AuaExecutionResult(auaResponse);
                })

                .doOnError(error ->
                        log.error(
                                "AUA execution failed. serviceId={}, taskId={}, txnId={}, operationType={}, error={}",
                                request.getServiceId(),
                                request.getTaskId(),
                                request.getTxnId(),
                                request.getOperationType(),
                                error.getMessage(),
                                error
                        )
                );
    }


    private IntegrationRequest buildIntegrationRequest(AuaApiConfigurationDTO api, String xml) {

        if (api == null) {
            throw new IllegalArgumentException("AUA API configuration is required");
        }

        if (api.getEndpoint() == null || api.getEndpoint().isBlank()) {

            throw new IllegalArgumentException("AUA API endpoint is not configured");
        }

        IntegrationRequest request = new IntegrationRequest();

        request.setProvider("XML");
        request.setUrl(api.getEndpoint());
        request.setMethod(HttpMethod.POST.name());
        request.setContentType(MediaType.APPLICATION_XML_VALUE);
        request.setBody(xml);

        log.info("ESB IntegrationRequest created. apiType={}, method={}, endpoint={}", api.getApiType(), HttpMethod.POST.name(), api.getEndpoint());

        return request;
    }

    private String extractResponseBody(IntegrationResponse response) {

        if (response == null) {
            throw new RuntimeException("Empty response received from ESB");
        }

        if (response.getBody() == null) {
            throw new RuntimeException("Empty provider response received from ESB");
        }

        Object body = response.getBody();

        if (body instanceof String) {
            return (String) body;
        }

        return body.toString();
    }

    private void validateIntegrationResponse(IntegrationResponse response) {

        if (response == null) {
            throw new RuntimeException("No response received from ESB");
        }

        if (!response.isSuccess()) {
            log.error("ESB invocation failed. statusCode={}, message={}", response.getStatusCode(), response.getMessage());
            throw new RuntimeException("ESB invocation failed: " + response.getMessage());
        }

        if (response.getBody() == null) {
            throw new RuntimeException("ESB returned empty provider response");
        }
    }


    private AuaApiConfigurationDTO findApi(ServiceJSONDTO metadata, String operationType) {

        if (metadata == null) {

            log.error("Service metadata is null while finding AUA API");
            throw new IllegalArgumentException("Service metadata not available");
        }

        if (metadata.getAuaApiConfigurations() == null || metadata.getAuaApiConfigurations().isEmpty()) {

            log.error("No AUA API configurations found in service metadata");
            throw new IllegalArgumentException("AUA API configuration not found for service");
        }

        if (operationType == null || operationType.isBlank()) {
            throw new IllegalArgumentException("AUA operation type is required");
        }

        for (AuaApiConfigurationDTO api : metadata.getAuaApiConfigurations()) {

            if (api == null || api.getApiType() == null) {
                continue;
            }

            if (operationType.trim().equalsIgnoreCase(api.getApiType().trim())) {

                log.info("AUA API configuration matched. operationType={}, apiType={}", operationType, api.getApiType());
                return api;
            }
        }

        log.error("AUA API not configured. operationType={}", operationType);
        throw new IllegalArgumentException("AUA API not configured for operation: " + operationType);
    }
}