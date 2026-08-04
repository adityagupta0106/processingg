package com.serviceplus.form.validation.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Date;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

@Table(name = "application_flow_status", schema = SP_SCHEMA_NAME)
public class ApplicationFlowStatusEntity implements Persistable<String> {

    @Id
    private String id;

    private String applicationId;

    private String activityType;

    private String txnId;

    private Integer completed;   //0 (incomplete),1 (complete), 2(complete/skipped by system)

    private String formId;

    private String dataId;

    private Integer serviceId;

    private String taskId;

    private LocalDateTime lastUpdate;

    private String tenantId;

    @Transient
    private Boolean userSubmissionRequired;

    @Transient
    private CurrentProcess currentProcess;

    public CurrentProcess getCurrentProcess() {
        return currentProcess;
    }

    public void setCurrentProcess(CurrentProcess currentProcess) {
        this.currentProcess = currentProcess;
    }

    @Transient
    private boolean newEntity = false;

    @Override
    @Transient
    public boolean isNew() {
        return this.newEntity;
    }

    @Override
    public String getId() {
        return id;
    }


    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public LocalDateTime getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(LocalDateTime lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public void setCompleted(Integer completed) {
        this.completed = completed;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public String getFormId() {
        return formId;
    }

    public void setFormId(String formId) {
        this.formId = formId;
    }

    public boolean isNewEntity() {
        return newEntity;
    }

    public void setNewEntity(boolean newEntity) {
        this.newEntity = newEntity;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getCompleted() {
        return completed;
    }

    public Boolean getUserSubmissionRequired() {
        return userSubmissionRequired;
    }

    public void setUserSubmissionRequired(Boolean userSubmissionRequired) {
        this.userSubmissionRequired = userSubmissionRequired;
    }
}

