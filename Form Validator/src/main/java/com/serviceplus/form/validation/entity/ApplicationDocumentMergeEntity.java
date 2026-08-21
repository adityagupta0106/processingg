package com.serviceplus.form.validation.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

@Table(name = "application_document_merge", schema = SP_SCHEMA_NAME)
public class ApplicationDocumentMergeEntity implements Persistable<String> {

    @Id
    private String id;

    private String applicationId;

    private String txnId;

    private String processId;

    private String taskId;

    private String referenceId;

    private String mergedUploadId;

    private String signedUploadId;

    private String status;

    private Integer createdBy;

    private LocalDateTime createdOn;

    private Integer updatedBy;

    private LocalDateTime updatedOn;

    private String tenantId;

    @Transient
    private boolean isNew = true;

    @Override
    public String getId() {
        return id;
    }

    @Override
    @Transient
    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean value) {
        this.isNew = value;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getMergedUploadId() {
        return mergedUploadId;
    }

    public void setMergedUploadId(String mergedUploadId) {
        this.mergedUploadId = mergedUploadId;
    }

    public String getSignedUploadId() {
        return signedUploadId;
    }

    public void setSignedUploadId(String signedUploadId) {
        this.signedUploadId = signedUploadId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(LocalDateTime createdOn) {
        this.createdOn = createdOn;
    }

    public Integer getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Integer updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(LocalDateTime updatedOn) {
        this.updatedOn = updatedOn;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}