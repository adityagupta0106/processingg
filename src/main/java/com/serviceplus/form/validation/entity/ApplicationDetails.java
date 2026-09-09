package com.serviceplus.form.validation.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

@Table(name = "application_details", schema = SP_SCHEMA_NAME)
public class ApplicationDetails implements Persistable<String> {

    @Id
    private String applicationId;

    private String draftReferenceNo;
    private String referenceNo;
    private Integer serviceId;
    private LocalDateTime applyDate;
    private String beneficiaryName;
    private LocalDateTime dueDate;
    private String status;
    private Integer appliedLocationId;
    private String appliedLocationName;
    private String grievanceApplicationId;
    private Long beneficiaryId;
    private String tenantId;
    private String serviceName;
    private Boolean isPriority;

    public ApplicationDetails(){}

    public ApplicationDetails(String applicationId, String draftReferenceNo, Integer serviceId, LocalDateTime applyDate, String beneficiaryName, String status, Long beneficiaryId, String tenantId) {
        this.applicationId = applicationId;
        this.draftReferenceNo = draftReferenceNo;
        this.serviceId = serviceId;
        this.applyDate = applyDate;
        this.beneficiaryName = beneficiaryName;
        this.status = status;
        this.beneficiaryId = beneficiaryId;
        this.tenantId = tenantId;
    }

    @Transient
    private boolean newEntity = false;

    @Override
    public String getId() {
        return this.applicationId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return this.newEntity;
    }

    // Getters and Setters
    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getDraftReferenceNo() {
        return draftReferenceNo;
    }

    public void setDraftReferenceNo(String draftReferenceNo) {
        this.draftReferenceNo = draftReferenceNo;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public LocalDateTime getApplyDate() {
        return applyDate;
    }

    public void setApplyDate(LocalDateTime applyDate) {
        this.applyDate = applyDate;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAppliedLocationId() {
        return appliedLocationId;
    }

    public void setAppliedLocationId(Integer appliedLocationId) {
        this.appliedLocationId = appliedLocationId;
    }

    public String getAppliedLocationName() {
        return appliedLocationName;
    }

    public void setAppliedLocationName(String appliedLocationName) {
        this.appliedLocationName = appliedLocationName;
    }

    public String getGrievanceApplicationId() {
        return grievanceApplicationId;
    }

    public void setGrievanceApplicationId(String grievanceApplicationId) {
        this.grievanceApplicationId = grievanceApplicationId;
    }

    public Long getBeneficiaryId() {
        return beneficiaryId;
    }

    public void setBeneficiaryId(Long beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isNewEntity() {
        return newEntity;
    }

    public void setNewEntity(boolean newEntity) {
        this.newEntity = newEntity;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

	public Boolean getIsPriority() {
		return isPriority;
	}

	public void setIsPriority(Boolean isPriority) {
		this.isPriority = isPriority;
	}
}
