package com.serviceplus.form.validation.dto;


public class WebServiceTaskDTO {

    private String taskId;

    private String taskName;

    private WebServiceDetails webserviceDetails;

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public String getTaskName() {
		return taskName;
	}

	public void setTaskName(String taskName) {
		this.taskName = taskName;
	}

	public WebServiceDetails getWebserviceDetails() {
		return webserviceDetails;
	}

	public void setWebserviceDetails(WebServiceDetails webserviceDetails) {
		this.webserviceDetails = webserviceDetails;
	}    
}
