package com.serviceplus.form.validation.dto;
import java.util.Date;

public class WorkflowAssignmentDTO {

    private String holderId;
    private Integer baseServiceId;
    private Integer serviceId;
    private String taskId;
    private String locationId;
    private Integer userId;
    private Integer assignedBy;
    private Integer versionNo;
    private Date crtOn;
    private Date updOn;
    private String tenantId;

    public WorkflowAssignmentDTO() {
    }

    public String getHolderId() {
        return holderId;
    }

    public void setHolderId(String holderId) {
        this.holderId = holderId;
    }

    public Integer getBaseServiceId() {
        return baseServiceId;
    }

    public void setBaseServiceId(Integer baseServiceId) {
        this.baseServiceId = baseServiceId;
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

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(Integer assignedBy) {
        this.assignedBy = assignedBy;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public Date getCrtOn() {
        return crtOn;
    }

    public void setCrtOn(Date crtOn) {
        this.crtOn = crtOn;
    }

    public Date getUpdOn() {
        return updOn;
    }

    public void setUpdOn(Date updOn) {
        this.updOn = updOn;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
