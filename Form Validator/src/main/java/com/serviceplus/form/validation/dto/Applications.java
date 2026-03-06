package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Applications {

    private String applicationId;
    private String applicationRefNo;
    private String applyDate;
    private String status;
    private Integer serviceId;
    private String draftRefNo;

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getApplyDate() {
        return applyDate;
    }

    public void setApplyDate(String applyDate) {
        this.applyDate = applyDate;
    }

    public String getApplicationRefNo() {
        return applicationRefNo;
    }

    public void setApplicationRefNo(String applicationRefNo) {
        this.applicationRefNo = applicationRefNo;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getDraftRefNo() {
        return draftRefNo;
    }

    public void setDraftRefNo(String draftRefNo) {
        this.draftRefNo = draftRefNo;
    }
}
