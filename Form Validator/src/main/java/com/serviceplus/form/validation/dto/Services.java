package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Services {

	@JsonIgnore
	private Integer serviceId;
	private String serviceName;
	@JsonIgnore
	private String formId;
	@JsonIgnore
	private String taskId;
	private String serviceKey;
	
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
	public String getServiceKey() {
		return serviceKey;
	}
	public void setServiceKey(String serviceKey) {
		this.serviceKey = serviceKey;
	}
}
