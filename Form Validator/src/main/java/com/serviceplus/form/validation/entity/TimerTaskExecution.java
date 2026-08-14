package com.serviceplus.form.validation.entity;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "timer_task_execution", schema = SP_SCHEMA_NAME)
public class TimerTaskExecution implements Persistable<String>{

    @Id
    private String id;

    private String applicationId;

    private String currentProcessId;

    private Integer serviceId;

    private Integer baseServiceId;

    private String taskId;
    
    private LocalDateTime dueDate;

    private String status;

    private String actionTaken;
    
    private LocalDateTime createdOn;

    private LocalDateTime executedOn;
    
    @Transient
    private boolean isNew = true;

    @Override
    @Transient
    public boolean isNew() {
        return isNew;
    }
    
    @Override
	public String getId() {
		return id;
	}

	public void setId(String id) {
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
	
	public LocalDateTime getDueDate() {
		return dueDate;
	}

	public void setDueDate(LocalDateTime dueDate) {
		this.dueDate = dueDate;
	}

	public LocalDateTime getCreatedOn() {
		return createdOn;
	}

	public void setCreatedOn(LocalDateTime createdOn) {
		this.createdOn = createdOn;
	}

	public LocalDateTime getExecutedOn() {
		return executedOn;
	}

	public void setExecutedOn(LocalDateTime executedOn) {
		this.executedOn = executedOn;
	}

	public void setNew(boolean isNew) {
		this.isNew = isNew;
	}
}
