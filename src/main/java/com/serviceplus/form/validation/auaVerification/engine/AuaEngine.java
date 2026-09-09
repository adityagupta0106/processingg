package com.serviceplus.form.validation.auaVerification.engine;

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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class AuaEngine {

    private static final Logger log = LoggerFactory.getLogger("applicationFlowLogger");

    private final AuaRequestBuilder requestBuilder;

    private final EsbClient esbClient;

    private final AuaResponseParser responseParser;


    public AuaEngine(AuaRequestBuilder requestBuilder, EsbClient esbClient, AuaResponseParser responseParser) {

        this.requestBuilder = requestBuilder;
        this.esbClient = esbClient;
        this.responseParser = responseParser;
    }


    /**
     * Execute any configured AUA operation.
     * <p>
     * Examples:
     * <p>
     * OTP_REQUEST
     * OTP_VALIDATION
     * DEMOGRAPHIC
     * EKYC
     * ESIGN
     * <p>
     * The operation determines which API configuration
     * is selected from service metadata.
     */
    public Mono<AuaExecutionResult> execute(ServiceJSONDTO metadata, AuaRequest request, AuaTransactionLog logEntry) {

        if (request == null) {
            return Mono.error(new IllegalArgumentException("AUA request is required"));
        }

        log.info("Starting AUA execution. serviceId={}, taskId={}, txnId={}, operationType={}",
                request.getServiceId(),
                request.getTaskId(),
                request.getTxnId(),
                request.getOperationType()
        );

        AuaApiConfigurationDTO api = findApi(metadata, request.getOperationType());


        log.info("AUA API selected. operationType={}, apiId={}, apiCode={}, auaId={}, providerName={}",
                request.getOperationType(),
                api.getApiId(), api.getApiCode(), api.getAuaId(), api.getProviderName());


        String xml = requestBuilder.buildRequest(api, request.getAttributes(),logEntry);


        log.info("AUA request XML generated. operationType={}, apiId={}, txnId={}", request.getOperationType(), api.getApiId(), request.getTxnId());

        log.info("AUA request XML. apiId={}, xml={}", api.getApiId(), xml);

        IntegrationRequest integrationRequest = buildIntegrationRequest(api, xml);

        log.info("Invoking AUA provider through ESB. operationType={}, apiId={}, endpoint={}", request.getOperationType(), api.getApiId(), api.getEndpoint());


//        AuaResponse auaResponse1 = new AuaResponse();
//
//        auaResponse1.setSuccess(true);
//        auaResponse1.setTransactionId(request.getTxnId());
//        auaResponse1.setErrorCode(null);
//        auaResponse1.setMessage("OTP requested successfully");
//
//        log.info(
//                "Returning mock AUA response directly to client. operationType={}, apiId={}, txnId={}, success={}",
//                request.getOperationType(),
//                api.getApiId(),
//                request.getTxnId(),
//                auaResponse1.isSuccess()
//        );
//
//        return Mono.just(
//                new AuaExecutionResult(
//                        auaResponse1,
//                        api.getAuaId(),
//                        api.getApiId()
//                )
//        );

        return esbClient.invoke(integrationRequest)

                .map(integrationResponse -> {

                    log.info("Received ESB response. operationType={}, apiId={}, success={}, statusCode={}", request.getOperationType(), api.getApiId(),
                            integrationResponse != null && integrationResponse.isSuccess(),
                            integrationResponse != null ? integrationResponse.getStatusCode() : null
                    );

                    validateIntegrationResponse(integrationResponse);

                    String providerResponse = extractResponseBody(integrationResponse);

                    log.info("Provider response received. operationType={}, apiId={}, txnId={}",
                            request.getOperationType(),
                            api.getApiId(),
                            request.getTxnId()
                    );

                    AuaResponse auaResponse = responseParser.parse(api, providerResponse);


                    log.info("AUA response parsed. operationType={}, apiId={}, txnId={}, success={}, providerTransactionIdPresent={}, errorCodePresent={},raw={}",
                            request.getOperationType(),
                            api.getApiId(),
                            request.getTxnId(),
                            auaResponse.isSuccess(),
                            auaResponse.getTransactionId() != null, auaResponse.getErrorCode() != null && !auaResponse.getErrorCode().isBlank(),
                            auaResponse.getRawResponse()
                    );

                    return new AuaExecutionResult(auaResponse, api.getAuaId(), api.getApiId());
                })

                .doOnError(error ->
                        log.error("AUA execution failed. serviceId={}, taskId={}, txnId={}, operationType={}, error={}", request.getServiceId(), request.getTaskId(), request.getTxnId(), request.getOperationType(), error.getMessage(), error));
    }


    private IntegrationRequest buildIntegrationRequest(AuaApiConfigurationDTO api, String xml) {

        if (api == null) {
            throw new IllegalArgumentException("AUA API configuration is required");
        }

        IntegrationRequest request = new IntegrationRequest();

        request.setProvider("XML");
        request.setUrl(api.getEndpoint());
        request.setMethod(HttpMethod.valueOf(api.getHttpMethod()).name());
        request.setProtocol(api.getProtocol());
        request.setContentType(MediaType.APPLICATION_XML_VALUE);

        request.setBody(xml);

        log.debug("ESB IntegrationRequest created. apiId={}, apiCode={}, protocol={}, method={}, endpoint={}",
                api.getApiId(),
                api.getApiCode(),
                api.getProtocol(),
                api.getHttpMethod(),
                api.getEndpoint()
        );

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


    /**
     * Validate ESB-level response.
     * <p>
     * This is different from AUA/provider-level
     * validation.
     * <p>
     * ESB success = request reached provider / ESB
     * <p>
     * AUA success = provider response says operation succeeded.
     */
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


    /**
     * Find API from frozen service metadata.
     * <p>
     * The operationType supplied by the request determines
     * which AUA API is executed.
     */
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


        /*
         * Avoid the previous getOperationType() issue if
         * you have an enum-backed DTO.
         *
         * Simple loop is also easier to debug.
         */
        for (AuaApiConfigurationDTO api : metadata.getAuaApiConfigurations()) {

            if (api == null) {
                continue;
            }

            String configuredOperation = api.getOperationType();

            if (configuredOperation == null) {
                continue;
            }

            if (operationType.trim().equalsIgnoreCase(configuredOperation.trim())) {
                return api;
            }
        }


        log.error("AUA API not configured. operationType={}", operationType);

        throw new IllegalArgumentException("AUA API not configured for operation: " + operationType);
    }
}