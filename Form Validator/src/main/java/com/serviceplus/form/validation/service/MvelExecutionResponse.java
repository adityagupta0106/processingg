package com.serviceplus.form.validation.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MvelExecutionResponse {
	
    private boolean success;
    private String error;
    private Map<String,Object> dataResponse=new HashMap<>();
    private List<Map<String,Object>> attributeResponse=new ArrayList<Map<String,Object>>();
    private Map<String, Map<String,List<String>>> taskLocationUserHolderMap;
    private List<String> nextNodeList=new ArrayList<String>();
    private Map<String, Date> timerDueDate=new HashMap<String, Date>(); 
	public boolean isSuccess() {
		return success;
	}
	public void setSuccess(boolean success) {
		this.success = success;
	}
	public String getError() {
		return error;
	}
	public void setError(String error) {
		this.error = error;
	}
	public Map<String, Object> getDataResponse() {
		return dataResponse;
	}
	public void setDataResponse(Map<String, Object> dataResponse) {
		this.dataResponse = dataResponse;
	}
	public List<Map<String, Object>> getAttributeResponse() {
		return attributeResponse;
	}
	public void setAttributeResponse(List<Map<String, Object>> attributeResponse) {
		this.attributeResponse = attributeResponse;
	}
	
	public Map<String, Map<String, List<String>>> getTaskLocationUserHolderMap() {
		return taskLocationUserHolderMap;
	}
	public void setTaskLocationUserHolderMap(Map<String, Map<String, List<String>>> taskLocationUserHolderMap) {
		this.taskLocationUserHolderMap = taskLocationUserHolderMap;
	}
	public List<String> getNextNodeList() {
		return nextNodeList;
	}
	public void setNextNodeList(List<String> nextNodeList) {
		this.nextNodeList = nextNodeList;
	}
	public Map<String, Date> getTimerDueDate() {
		return timerDueDate;
	}
	public void setTimerDueDate(Map<String, Date> timerDueDate) {
		this.timerDueDate = timerDueDate;
	}
    
	
}

