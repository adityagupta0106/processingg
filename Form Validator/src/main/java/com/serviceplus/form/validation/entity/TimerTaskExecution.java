package com.serviceplus.form.validation.entity;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "timer_task_execution", schema = SP_SCHEMA_NAME)
public class TimerTaskExecution {

    @Id
    private Long id;

    private String applicationId;

    private String currentProcessId;

    private Integer serviceId;

    private Integer baseServiceId;

    private String taskId;
    
    private Date dueDate;

    private String status;

    private String actionTaken;
    
    private Date createdOn;

    private Date executedOn;
    
    
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

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public Date getDueDate() {
		return dueDate;
	}

	public void setDueDate(Date dueDate) {
		this.dueDate = dueDate;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getActionTaken() {
		return actionTaken;
	}

	public void setActionTaken(String actionTaken) {
		this.actionTaken = actionTaken;
	}

	public Date getCreatedOn() {
		return createdOn;
	}

	public void setCreatedOn(Date createdOn) {
		this.createdOn = createdOn;
	}

	public Date getExecutedOn() {
		return executedOn;
	}

	public void setExecutedOn(Date executedOn) {
		this.executedOn = executedOn;
	}
}
