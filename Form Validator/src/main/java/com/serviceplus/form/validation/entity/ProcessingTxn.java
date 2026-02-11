package com.serviceplus.form.validation.entity;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "processing_transactions" , schema = SP_SCHEMA_NAME)
public class ProcessingTxn implements Persistable<String>{

	@Id
	private String txnId;
	
	private String formId;
	
	private Integer serviceId;
	
	private String taskId;

    private String applicationId;

	private Integer userId;

    private String userIp;
	
	private LocalDateTime startTime;
	
	private LocalDateTime endTime;
	
	private String tenantId;

    private String activityType;
	
	@Transient
    private boolean newEntity = false;

	@Override
    @Transient
    public boolean isNew() {
        return this.newEntity;
    }
	
	@Override
    public String getId() {
        return txnId;
    }
	
	public ProcessingTxn() {
		super();
	}

	public ProcessingTxn(String txnId, String formId, Integer serviceId, String taskId, LocalDateTime pageStartTime,
                         LocalDateTime formEndTime, Integer userId, String tenantId, String userIp, String applicationId) {
		super();
		this.txnId = txnId;
		this.formId = formId;
		this.serviceId = serviceId;
		this.taskId = taskId;
		this.endTime = formEndTime;
		this.userId = userId;
		this.tenantId = tenantId;
		this.userIp = userIp;
        this.applicationId = applicationId;
	}

	public String getTxnId() {
		return txnId;
	}

	public void setTxnId(String txnId) {
		this.txnId = txnId;
	}

	public String getFormId() {
		return formId;
	}

	public void setFormId(String formId) {
		this.formId = formId;
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

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getUserId() {
		return userId;
	}

	public void setUserId(Integer userId) {
		this.userId = userId;
	}

	public String getTenantId() {
		return tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

	public boolean isNewEntity() {
		return newEntity;
	}

	public void setNewEntity(boolean newEntity) {
		this.newEntity = newEntity;
	}

    public String getUserIp() {
		return userIp;
	}

	public void setUserIp(String userIp) {
		this.userIp = userIp;
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
}
