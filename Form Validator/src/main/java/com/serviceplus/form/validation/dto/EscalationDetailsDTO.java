package com.serviceplus.form.validation.dto;

import java.util.List;

public class EscalationDetailsDTO {

    private String taskId;

    private String taskName;

    private TimePeriod slaPeriod;

    private TimePeriod escalationPeriod;

    private String taskType;

    private List<String> defaultActions;

    private String mvelExpression;
    
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

    public TimePeriod getSlaPeriod() {
		return slaPeriod;
	}

	public void setSlaPeriod(TimePeriod slaPeriod) {
		this.slaPeriod = slaPeriod;
	}

	public TimePeriod getEscalationPeriod() {
		return escalationPeriod;
	}

	public void setEscalationPeriod(TimePeriod escalationPeriod) {
		this.escalationPeriod = escalationPeriod;
	}

	public String getTaskType() {
		return taskType;
	}

	public void setTaskType(String taskType) {
		this.taskType = taskType;
	}

	public List<String> getDefaultActions() {
        return defaultActions;
    }

    public void setDefaultActions(List<String> defaultActions) {
        this.defaultActions = defaultActions;
    }

    public String getMvelExpression() {
        return mvelExpression;
    }

    public void setMvelExpression(String mvelExpression) {
        this.mvelExpression = mvelExpression;
    }
}
