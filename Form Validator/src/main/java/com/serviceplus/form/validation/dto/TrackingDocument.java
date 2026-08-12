package com.serviceplus.form.validation.dto;

import java.util.List;

public class TrackingDocument {

    private String uploadId;
    private String documentName;
    private String sourceType;
    private String referenceId;
    private List<String> viewPermission;

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public List<String> getViewPermission() {
        return viewPermission;
    }

    public void setViewPermission(List<String> viewPermission) {
        this.viewPermission = viewPermission;
    }
}
