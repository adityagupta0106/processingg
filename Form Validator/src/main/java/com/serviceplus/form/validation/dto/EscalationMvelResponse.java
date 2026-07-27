package com.serviceplus.form.validation.dto;

import java.util.List;
import java.util.Map;

public class EscalationMvelResponse {

	private boolean success;

    private String action;
    
    private Map<String, Map<String,List<String>>> taskLocationUserHolderMap;
    
    private List<String> nextNodeList;

    private String error;
    

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
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
	
    
    
}
