package com.serviceplus.form.validation.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiExecutionResponse {

    private String apiId;

    private String validationToken;

    private Object response;

    private Map<String, DynamicAttributeData> normalizedResponse = new LinkedHashMap<>();

    public static class DynamicAttributeData {

        private String targetType;

        private String dataType;

        private List<Map<String, Object>> data = new ArrayList<>();

        public String getTargetType() {
            return targetType;
        }

        public void setTargetType(String targetType) {
            this.targetType = targetType;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        public List<Map<String, Object>> getData() {
            return data;
        }

        public void setData(List<Map<String, Object>> data) {
            this.data = data;
        }
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getValidationToken() {
        return validationToken;
    }

    public void setValidationToken(String validationToken) {
        this.validationToken = validationToken;
    }

    public Map<String, DynamicAttributeData> getNormalizedResponse() {
        return normalizedResponse;
    }

    public void setNormalizedResponse(Map<String, DynamicAttributeData> normalizedResponse) {
        this.normalizedResponse = normalizedResponse;
    }

    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
    }
}
