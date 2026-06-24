package com.serviceplus.form.validation.dto;

import com.serviceplus.form.validation.entity.CurrentProcess;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

public class InboxKafka {

    private Long locationId;
    private String locationName;
    private String serviceName;
    private String applicationRefNo;
    private Long appliedBy;
    private String beneficiaryName;
    private LocalDateTime applyDate;

    private List<TaskAvailableOfficeLocation> officeDetails;
    private List<CurrentProcess> processList;

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public List<CurrentProcess> getProcessList() {
        return processList;
    }

    public void setProcessList(List<CurrentProcess> processList) {
        this.processList = processList;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public List<TaskAvailableOfficeLocation> getOfficeDetails() {
        return officeDetails;
    }

    public void setOfficeDetails(List<TaskAvailableOfficeLocation> officeDetails) {
        this.officeDetails = officeDetails;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getApplicationRefNo() {
        return applicationRefNo;
    }

    public void setApplicationRefNo(String applicationRefNo) {
        this.applicationRefNo = applicationRefNo;
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
}
