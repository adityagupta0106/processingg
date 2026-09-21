package com.serviceplus.form.validation.auaVerification.dto;

public class AuaExecutionResult {

    private AuaResponse response;
    private Long providerId;
    private Long apiId;

    public AuaExecutionResult() {
    }

    public AuaExecutionResult(AuaResponse response, Long providerId, Long apiId) {
        this.response = response;
        this.providerId = providerId;
        this.apiId = apiId;
    }

    public AuaExecutionResult(AuaResponse auaResponse) {
        this.response = auaResponse;
    }

    public AuaResponse getResponse() {
        return response;
    }

    public void setResponse(AuaResponse response) {
        this.response = response;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public Long getApiId() {
        return apiId;
    }

    public void setApiId(Long apiId) {
        this.apiId = apiId;
    }
}
