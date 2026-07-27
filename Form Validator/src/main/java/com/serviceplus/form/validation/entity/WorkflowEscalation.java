package com.serviceplus.form.validation.entity;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;



@Table(name="workflow_escalation", schema = SP_SCHEMA_NAME)
public class WorkflowEscalation {

    @Id
    private Long id;

    private String applicationId;

    private Integer serviceId;

    private String currentProcessId;

    private String taskId;

    private Date executeOn;

    private String status;

    private Integer retryCount;

    private String action;

    private String mvelExpression;

    private String escalationJson;

    private Date createdOn;

    private Date modifiedOn;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public String getCurrentProcessId() {
		return currentProcessId;
	}

	public void setCurrentProcessId(String currentProcessId) {
		this.currentProcessId = currentProcessId;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public Date getExecuteOn() {
		return executeOn;
	}

	public void setExecuteOn(Date executeOn) {
		this.executeOn = executeOn;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Integer getRetryCount() {
		return retryCount;
	}

	public void setRetryCount(Integer retryCount) {
		this.retryCount = retryCount;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getMvelExpression() {
		return mvelExpression;
	}

	public void setMvelExpression(String mvelExpression) {
		this.mvelExpression = mvelExpression;
	}

	public String getEscalationJson() {
		return escalationJson;
	}

	public void setEscalationJson(String escalationJson) {
		this.escalationJson = escalationJson;
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
