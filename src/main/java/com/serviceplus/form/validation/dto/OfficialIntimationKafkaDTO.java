package com.serviceplus.form.validation.dto;

import java.time.LocalDateTime;
import java.util.List;

public class OfficialIntimationKafkaDTO {

    private String associatedTaskId;

    private String applicationId;
    private String applicationRefNo;

    private Integer serviceId;
    private Integer baseServiceId;
    private String serviceName;

    private String currentProcessId;

    private String currentTaskId;
    private String currentTaskName;

    private String tenantId;

    private Boolean allowApplicationView;
    private Boolean allowHistoryView;
    private Boolean autoClear;

    private Long appliedBy;
    private String beneficiaryName;
    private LocalDateTime applyDate;

    private List<OfficeDetailsDTO.OfficeUnitData> allowedOffices;

    public String getAssociatedTaskId() {
        return associatedTaskId;
    }

    public void setAssociatedTaskId(String associatedTaskId) {
        this.associatedTaskId = associatedTaskId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getApplicationRefNo() {
        return applicationRefNo;
    }

    public void setApplicationRefNo(String applicationRefNo) {
        this.applicationRefNo = applicationRefNo;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public Integer getBaseServiceId() {
        return baseServiceId;
    }

    public void setBaseServiceId(Integer baseServiceId) {
        this.baseServiceId = baseServiceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getCurrentProcessId() {
        return currentProcessId;
    }

    public void setCurrentProcessId(String currentProcessId) {
        this.currentProcessId = currentProcessId;
    }

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    public void setCurrentTaskId(String currentTaskId) {
        this.currentTaskId = currentTaskId;
    }

    public String getCurrentTaskName() {
        return currentTaskName;
    }

    public void setCurrentTaskName(String currentTaskName) {
        this.currentTaskName = currentTaskName;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Boolean getAllowApplicationView() {
        return allowApplicationView;
    }

    public void setAllowApplicationView(Boolean allowApplicationView) {
        this.allowApplicationView = allowApplicationView;
    }

    public Boolean getAllowHistoryView() {
        return allowHistoryView;
    }

    public void setAllowHistoryView(Boolean allowHistoryView) {
        this.allowHistoryView = allowHistoryView;
    }

    public Boolean getAutoClear() {
        return autoClear;
    }

    public void setAutoClear(Boolean autoClear) {
        this.autoClear = autoClear;
    }

    public Long getAppliedBy() {
        return appliedBy;
    }

    public void setAppliedBy(Long appliedBy) {
        this.appliedBy = appliedBy;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public LocalDateTime getApplyDate() {
        return applyDate;
    }

    public void setApplyDate(LocalDateTime applyDate) {
        this.applyDate = applyDate;
    }

    public List<OfficeDetailsDTO.OfficeUnitData> getAllowedOffices() {
        return allowedOffices;
    }

    public void setAllowedOffices(List<OfficeDetailsDTO.OfficeUnitData> allowedOffices) {
        this.allowedOffices = allowedOffices;
    }
}
