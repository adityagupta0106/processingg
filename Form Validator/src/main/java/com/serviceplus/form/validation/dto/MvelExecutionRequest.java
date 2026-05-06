package com.serviceplus.form.validation.dto;

import java.util.List;
import java.util.Map;

public class MvelExecutionRequest {

    private Integer serviceId;
    private String txnId;
    private String activityId;
    private String TriggerPoint;
    private String applicationId;
    private String currentProcessId;
    private String taskId;
    private Long functionId;
    private String formData;
    private Map<String, Object> inputAttributeMap;
    private Map<String, Object> applicationDetails;
    private Map<String, Object> serviceDetails;
    private Map<String,List<Integer>> userList;
    private List<String> nextNodeList;
	public Integer getServiceId() {
		return serviceId;
	}
	public void setServiceId(Integer serviceId) {
		this.serviceId = serviceId;
	}
	public String getTxnId() {
		return txnId;
	}
	public void setTxnId(String txnId) {
		this.txnId = txnId;
	}
	public String getActivityId() {
		return activityId;
	}
	public void setActivityId(String activityId) {
		this.activityId = activityId;
	}
	public String getTriggerPoint() {
		return TriggerPoint;
	}
	public void setTriggerPoint(String triggerPoint) {
		TriggerPoint = triggerPoint;
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
	public String getTaskId() {
		return taskId;
	}
	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}
	public Long getFunctionId() {
		return functionId;
	}
	public void setFunctionId(Long functionId) {
		this.functionId = functionId;
	}
	public String getFormData() {
		return formData;
	}
	public void setFormData(String formData) {
		this.formData = formData;
	}
	public Map<String, Object> getInputAttributeMap() {
		return inputAttributeMap;
	}
	public void setInputAttributeMap(Map<String, Object> inputAttributeMap) {
		this.inputAttributeMap = inputAttributeMap;
	}
	public Map<String, Object> getApplicationDetails() {
		return applicationDetails;
	}
	public void setApplicationDetails(Map<String, Object> applicationDetails) {
		this.applicationDetails = applicationDetails;
	}
	public Map<String, Object> getServiceDetails() {
		return serviceDetails;
	}
	public void setServiceDetails(Map<String, Object> serviceDetails) {
		this.serviceDetails = serviceDetails;
	}
	public Map<String, List<Integer>> getUserList() {
		return userList;
	}
	public void setUserList(Map<String, List<Integer>> userList) {
		this.userList = userList;
	}
	public List<String> getNextNodeList() {
		return nextNodeList;
	}
	public void setNextNodeList(List<String> nextNodeList) {
		this.nextNodeList = nextNodeList;
	}
}

