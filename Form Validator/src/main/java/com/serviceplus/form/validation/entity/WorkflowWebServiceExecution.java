package com.serviceplus.form.validation.entity;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name="workflow_webservice_execution", schema = SP_SCHEMA_NAME)
public class WorkflowWebServiceExecution {

    @Id
    @Column("execution_id")
    private Long executionId;

    @Column("application_id")
    private String applicationId;

    @Column("service_id")
    private Integer serviceId;

    @Column("base_service_id")
    private Integer baseServiceId;

    @Column("current_process_id")
    private String currentProcessId;

    @Column("current_task_id")
    private String currentTaskId;

    @Column("api_id")
    private String apiId;

    @Column("status")
    private String status;

    @Column("attempt_count")
    private Integer attemptCount;

    @Column("max_attempt")
    private Integer maxAttempt;

    @Column("retry_interval")
    private Integer retryInterval;

    @Column("retry_interval_unit")
    private String retryIntervalUnit;

    @Column("next_retry_time")
    private Date nextRetryTime;

    @Column("api_response")
    private String apiResponse;

    @Column("normalized_response")
    private String normalizedResponse;

    @Column("validation_token")
    private String validationToken;

    @Column("form_data_id")
    private String formDataId;

    @Column("error_message")
    private String errorMessage;

    @Column("created_on")
    private Date createdOn;

    @Column("modified_on")
    private Date modifiedOn;

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
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

    public String getCurrentProcessId() {
        return currentProcessId;
    }

    public void setCurrentProcessId(String currentProcessId) {
        this.currentProcessId = currentProcessId;
    }

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    public void setCurrentTaskId(String currentTaskId) {
        this.currentTaskId = currentTaskId;
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Integer getMaxAttempt() {
        return maxAttempt;
    }

    public void setMaxAttempt(Integer maxAttempt) {
        this.maxAttempt = maxAttempt;
    }

    public Integer getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(Integer retryInterval) {
        this.retryInterval = retryInterval;
    }

    public String getRetryIntervalUnit() {
        return retryIntervalUnit;
    }

    public void setRetryIntervalUnit(String retryIntervalUnit) {
        this.retryIntervalUnit = retryIntervalUnit;
    }

    public Date getNextRetryTime() {
        return nextRetryTime;
    }

    public void setNextRetryTime(Date nextRetryTime) {
        this.nextRetryTime = nextRetryTime;
    }

    public String getApiResponse() {
        return apiResponse;
    }

    public void setApiResponse(String apiResponse) {
        this.apiResponse = apiResponse;
    }

    public String getNormalizedResponse() {
        return normalizedResponse;
    }

    public void setNormalizedResponse(String normalizedResponse) {
        this.normalizedResponse = normalizedResponse;
    }

    public String getValidationToken() {
        return validationToken;
    }

    public void setValidationToken(String validationToken) {
        this.validationToken = validationToken;
    }

    public String getFormDataId() {
        return formDataId;
    }

    public void setFormDataId(String formDataId) {
        this.formDataId = formDataId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Date getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    public Date getModifiedOn() {
        return modifiedOn;
    }

    public void setModifiedOn(Date modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
}