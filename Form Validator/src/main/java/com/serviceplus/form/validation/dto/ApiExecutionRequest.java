package com.serviceplus.form.validation.dto;


import java.util.LinkedHashMap;
import java.util.Map;

public class ApiExecutionRequest {

	private String applicationId;
	private String currentProcessId;
	private Integer serviceId;
    private Integer baseServiceId;
    private String apiId;
    private Object apiDefinition;
    private Map<String, Object> attributeValues = new LinkedHashMap<>();
    private boolean validationRequired;
    
    public String getApplicationId() {
		return applicationId;
	}

	public void setApplicationId(String applicationId) {
		this.applicationId = applicationId;
	}

	public String getCurrentProcessId() {
		return currentProcessId;
	}

	public void setCurrentProcessId(String currentProcessId) {
		this.currentProcessId = currentProcessId;
	}
	
	public Integer getServiceId() {
		return serviceId;
	}

	public void setServiceId(Integer serviceId) {
		this.serviceId = serviceId;
	}

	public Integer getBaseServiceId() {
		return baseServiceId;
	}

	public void setBaseServiceId(Integer baseServiceId) {
		this.baseServiceId = baseServiceId;
	}

	public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public Map<String, Object> getAttributeValues() {
        return attributeValues;
    }

    public void setAttributeValues(Map<String, Object> attributeValues) {
        this.attributeValues = attributeValues;
    }

    public boolean isValidationRequired() {
        return validationRequired;
    }

    public void setValidationRequired(boolean validationRequired) {
        this.validationRequired = validationRequired;
    }

    public Object getApiDefinition() {
        return apiDefinition;
    }

    public void setApiDefinition(Object apiDefinition) {
        this.apiDefinition = apiDefinition;
    }
}
