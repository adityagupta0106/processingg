package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowInboxResponse {

    private String applId;

    private String applRefNo;

    private Integer serviceId;

    private String serviceName;

    private String taskName;

    @JsonIgnore
    private String formId;

    @JsonIgnore
    private String taskId;

    @JsonIgnore
    private String taskType;

    private Integer baseServiceId;

    private String currentProcessId;

    @JsonIgnore
    private Integer locationId;

    @JsonIgnore
    private Date applRecievedOn;

    private String serviceKey;

    private List<String> locations;

    private String receivedDate;

    public String getApplId() {
        return applId;
    }

    public void setApplId(String applId) {
        this.applId = applId;
    }

    public String getApplRefNo() {
        return applRefNo;
    }

    public void setApplRefNo(String applRefNo) {
        this.applRefNo = applRefNo;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getFormId() {
        return formId;
    }

    public void setFormId(String formId) {
        this.formId = formId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Integer getBaseServiceId() {
        return baseServiceId;
    }

    public void setBaseServiceId(Integer baseServiceId) {
        this.baseServiceId = baseServiceId;
    }

    public String getCurrentProcessId() {
        return currentProcessId;
    }

    public void setCurrentProcessId(String currentProcessId) {
        this.currentProcessId = currentProcessId;
    }

    public Integer getLocationId() {
        return locationId;
    }

    public void setLocationId(Integer locationId) {
        this.locationId = locationId;
    }

    public Date getApplRecievedOn() {
        return applRecievedOn;
    }

    public void setApplRecievedOn(Date applRecievedOn) {
        this.applRecievedOn = applRecievedOn;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(String receivedDate) {
        this.receivedDate = receivedDate;
    }
}
