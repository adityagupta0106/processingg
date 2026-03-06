package com.serviceplus.form.validation.dto;

import java.util.List;
import com.serviceplus.form.validation.dto.ServiceMeta.AvailableApplyLocations;

public class TaskAvailableOfficeLocation {

    private String taskId;
    private List<AvailableApplyLocations> allowedOffices;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public List<AvailableApplyLocations> getAllowedOffices() {
        return allowedOffices;
    }

    public void setAllowedOffices(List<AvailableApplyLocations> allowedOffices) {
        this.allowedOffices = allowedOffices;
    }
}
