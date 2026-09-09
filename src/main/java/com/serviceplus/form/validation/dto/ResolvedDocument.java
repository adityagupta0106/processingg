package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResolvedDocument {

    private String documentId;

    private String referenceId;

    private String documentName;

    private String sourceType;

    private String previewUrl;

    private String status;

    @JsonIgnore
    private String uploadId;

    private String downloadUrl;

    private String thumbnailUrl;

    private List<String> digitalSignatureModes = new ArrayList<>();

    private String base64Content;

    private List<DocumentGenerationDetails.DocMappingDTO.LabelValueDTO> fileTypes;

    private Boolean fileUploadOptional;

    private CreateUploadSessionsResponse.UploadSessionItem uploadSession;

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getDigitalSignatureModes() {
        return digitalSignatureModes;
    }

    public void setDigitalSignatureModes(List<String> digitalSignatureModes) {
        this.digitalSignatureModes = digitalSignatureModes;
    }

    public String getBase64Content() {
        return base64Content;
    }

    public void setBase64Content(String base64Content) {
        this.base64Content = base64Content;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public List<DocumentGenerationDetails.DocMappingDTO.LabelValueDTO> getFileTypes() {
        return fileTypes;
    }

    public void setFileTypes(List<DocumentGenerationDetails.DocMappingDTO.LabelValueDTO> fileTypes) {
        this.fileTypes = fileTypes;
    }

    public Boolean getFileUploadOptional() {
        return fileUploadOptional;
    }

    public void setFileUploadOptional(Boolean fileUploadOptional) {
        this.fileUploadOptional = fileUploadOptional;
    }

    public CreateUploadSessionsResponse.UploadSessionItem getUploadSession() {
        return uploadSession;
    }

    public void setUploadSession(CreateUploadSessionsResponse.UploadSessionItem uploadSession) {
        this.uploadSession = uploadSession;
    }
}
