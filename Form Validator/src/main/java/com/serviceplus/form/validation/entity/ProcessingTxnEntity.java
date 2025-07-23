package com.serviceplus.form.validation.entity;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "processing_transactions" , schema = SP_SCHEMA_NAME)
public class ProcessingTxnEntity  implements Persistable<String>{

	@Id
	private String txnId;
	
	private String formId;
	
	private Integer serviceId;
	
	private String taskId;
	
	private Integer userId;
	
	private LocalDateTime formStartTime;
	
	private LocalDateTime formEndTime;
	
	private String tenantId;
	
	private String actualApplicationId;
	
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
	
	public ProcessingTxnEntity() {
		super();
	}

	public ProcessingTxnEntity(String txnId, String formId, Integer serviceId, String taskId, LocalDateTime pageStartTime,
			LocalDateTime formEndTime,Integer userId,String tenantId) {
		super();
		this.txnId = txnId;
		this.formId = formId;
		this.serviceId = serviceId;
		this.taskId = taskId;
		this.formStartTime = pageStartTime;
		this.formEndTime = formEndTime;
		this.userId = userId;
		this.tenantId = tenantId;
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

	public LocalDateTime getFormStartTime() {
		return formStartTime;
	}

	public void setFormStartTime(LocalDateTime formStartTime) {
		this.formStartTime = formStartTime;
	}

	public LocalDateTime getFormEndTime() {
		return formEndTime;
	}

	public void setFormEndTime(LocalDateTime formEndTime) {
		this.formEndTime = formEndTime;
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

	public String getActualApplicationId() {
		return actualApplicationId;
	}

	public void setActualApplicationId(String actualApplicationId) {
		this.actualApplicationId = actualApplicationId;
	}

}
