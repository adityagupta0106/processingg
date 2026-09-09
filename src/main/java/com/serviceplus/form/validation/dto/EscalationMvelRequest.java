package com.serviceplus.form.validation.dto;

import java.util.List;
import java.util.Map;

public class EscalationMvelRequest {

    private String expression;

    private Integer serviceId;

    private String applicationId;
    
    private String taskId;
    
    private Map<String,Object> applicationDetails;

    private Map<String,Object> serviceDetails;
    
    private Map<String, Map<String,List<String>>> taskLocationUserHolderMap;
    
    private List<String> nextNodeList;

	public String getExpression() {
		return expression;
	}

	public void setExpression(String expression) {
		this.expression = expression;
	}

	public Integer getServiceId() {
		return serviceId;
	}

	public void setServiceId(Integer serviceId) {
		this.serviceId = serviceId;
	}

	public String getApplicationId() {
		return applicationId;
	}

	public void setApplicationId(String applicationId) {
		this.applicationId = applicationId;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
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

	public List<String> getNextNodeList() {
		return nextNodeList;
	}

	public void setNextNodeList(List<String> nextNodeList) {
		this.nextNodeList = nextNodeList;
	}

	public Map<String, Map<String, List<String>>> getTaskLocationUserHolderMap() {
		return taskLocationUserHolderMap;
	}

	public void setTaskLocationUserHolderMap(Map<String, Map<String, List<String>>> taskLocationUserHolderMap) {
		this.taskLocationUserHolderMap = taskLocationUserHolderMap;
	}    
}
