package com.serviceplus.form.validation.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

@Table(name = "current_process", schema = SP_SCHEMA_NAME)
public class CurrentProcess implements Persistable<String> {

    @Id
    private String processId;

    private Integer serviceId;

    private String currentTask;

    private String currentTaskName;

    private String previousTask;

    private String previousTaskName;

    private String previousProcessId;

    private Integer actionCode;

    private String actionTaken="N";

    private String isParallel;

    private LocalDateTime actionOn;

    private LocalDateTime initiatedOn;

    private Long userId;

    private String userIp;

    private String tenantId;

    private String applicationId;

    private String dataId;

    @Transient
    private boolean newEntity = false;

    @Override
    @Transient
    public boolean isNew() {
        return this.newEntity;
    }

    @Override
    public String getId() {
        return processId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getCurrentTask() {
        return currentTask;
    }

    public void setCurrentTask(String currentTask) {
        this.currentTask = currentTask;
    }

    public String getCurrentTaskName() {
        return currentTaskName;
    }

    public void setCurrentTaskName(String currentTaskName) {
        this.currentTaskName = currentTaskName;
    }

    public String getPreviousTask() {
        return previousTask;
    }

    public void setPreviousTask(String previousTask) {
        this.previousTask = previousTask;
    }

    public String getPreviousTaskName() {
        return previousTaskName;
    }

    public void setPreviousTaskName(String previousTaskName) {
        this.previousTaskName = previousTaskName;
    }

    public String getPreviousProcessId() {
        return previousProcessId;
    }

    public void setPreviousProcessId(String previousProcessId) {
        this.previousProcessId = previousProcessId;
    }

    public Integer getActionCode() {
        return actionCode;
    }

    public void setActionCode(Integer actionCode) {
        this.actionCode = actionCode;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String actionTaken) {
        this.actionTaken = actionTaken;
    }

    public String getIsParallel() {
        return isParallel;
    }

    public void setIsParallel(String isParallel) {
        this.isParallel = isParallel;
    }

    public LocalDateTime getActionOn() {
        return actionOn;
    }

    public void setActionOn(LocalDateTime actionOn) {
        this.actionOn = actionOn;
    }

    public LocalDateTime getInitiatedOn() {
        return initiatedOn;
    }

    public void setInitiatedOn(LocalDateTime initiatedOn) {
        this.initiatedOn = initiatedOn;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserIp() {
        return userIp;
    }

    public void setUserIp(String userIp) {
        this.userIp = userIp;
    }

    public boolean isNewEntity() {
        return newEntity;
    }

    public void setNewEntity(boolean newEntity) {
        this.newEntity = newEntity;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
}

