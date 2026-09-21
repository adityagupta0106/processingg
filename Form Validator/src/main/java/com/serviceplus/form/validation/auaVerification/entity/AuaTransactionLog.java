package com.serviceplus.form.validation.auaVerification.entity;

import java.time.OffsetDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import static com.serviceplus.form.validation.utility.ApplicationConstants.SP_SCHEMA_NAME;

@Table(name = "aua_transaction_log", schema = SP_SCHEMA_NAME)
public class AuaTransactionLog implements Persistable<Long> {

    @Id
    private Long id;

    private String txnId;

    private Integer rowNo;

    private String attributeId;

    private Integer serviceId;

    private String taskId;

    private String tenantId;

    private String operationType;

    private String status;

    private Long providerId;

    private Long apiId;

    private String providerTxnId;

    private String errorCode;

    private String errorMessage;

    private OffsetDateTime completedAt;

    private OffsetDateTime crDate;

    private OffsetDateTime upDate;

    @Transient
    private boolean newEntity = false;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    @Transient
    public boolean isNew() {
        return newEntity;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getAttributeId() {
        return attributeId;
    }

    public void setAttributeId(String attributeId) {
        this.attributeId = attributeId;
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

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public Long getApiId() {
        return apiId;
    }

    public void setApiId(Long apiId) {
        this.apiId = apiId;
    }

    public String getProviderTxnId() {
        return providerTxnId;
    }

    public void setProviderTxnId(String providerTxnId) {
        this.providerTxnId = providerTxnId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public OffsetDateTime getCrDate() {
        return crDate;
    }

    public void setCrDate(OffsetDateTime crDate) {
        this.crDate = crDate;
    }

    public OffsetDateTime getUpDate() {
        return upDate;
    }

    public void setUpDate(OffsetDateTime upDate) {
        this.upDate = upDate;
    }

    public boolean isNewEntity() {
        return newEntity;
    }

    public void setNewEntity(boolean newEntity) {
        this.newEntity = newEntity;
    }
}