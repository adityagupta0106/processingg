package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HandlerResponse {

    private Map<String,Object> data;

    private String txnId;
    private String applicationId;
    private String activityType;
    private List<ServiceMeta.AvailableApplyLocations> applyLocations;
    private boolean activityEnd;

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public List<ServiceMeta.AvailableApplyLocations> getApplyLocations() {
        return applyLocations;
    }

    public void setApplyLocations(List<ServiceMeta.AvailableApplyLocations> applyLocations) {
        this.applyLocations = applyLocations;
    }

    public boolean isActivityEnd() {
        return activityEnd;
    }

    public void setActivityEnd(boolean activityEnd) {
        this.activityEnd = activityEnd;
    }
}
