package com.serviceplus.form.validation.dto;

public class TimerTaskDTO {

    private String taskId;
    
    private Integer executionPeriod;

    private String executionPeriodUnit;

    private Integer behaviour;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

	public Integer getExecutionPeriod() {
		return executionPeriod;
	}

	public void setExecutionPeriod(Integer executionPeriod) {
		this.executionPeriod = executionPeriod;
	}

	public String getExecutionPeriodUnit() {
		return executionPeriodUnit;
	}

	public void setExecutionPeriodUnit(String executionPeriodUnit) {
		this.executionPeriodUnit = executionPeriodUnit;
	}

	public Integer getBehaviour() {
		return behaviour;
	}

	public void setBehaviour(Integer behaviour) {
		this.behaviour = behaviour;
	}
    
    
}
