package com.serviceplus.form.validation.dto;

import java.util.List;

public class CreateUploadSessionsRequest {

    private Long userId;
    private String sourceService;
    private List<FileUploadRequest> files;

    public static class FileUploadRequest {
        private String referenceId;
        private String category;

        private Long maxFileSize;
        private Long minFileSize;

        private List<String> allowedMime;
        private Boolean chunkedUpload;

        private Integer expiresInMinutes;
        private String fileName;

        private String documentId;
        private String functionality;

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Long getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(Long maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        public Integer getExpiresInMinutes() {
            return expiresInMinutes;
        }

        public void setExpiresInMinutes(Integer expiresInMinutes) {
            this.expiresInMinutes = expiresInMinutes;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getReferenceId() {
            return referenceId;
        }

        public void setReferenceId(String referenceId) {
            this.referenceId = referenceId;
        }

        public Long getMinFileSize() {
            return minFileSize;
        }

        public void setMinFileSize(Long minFileSize) {
            this.minFileSize = minFileSize;
        }

        public List<String> getAllowedMime() {
            return allowedMime;
        }

        public void setAllowedMime(List<String> allowedMime) {
            this.allowedMime = allowedMime;
        }

        public Boolean getChunkedUpload() {
            return chunkedUpload;
        }

        public void setChunkedUpload(Boolean chunkedUpload) {
            this.chunkedUpload = chunkedUpload;
        }

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }

        public String getFunctionality() {
            return functionality;
        }

        public void setFunctionality(String functionality) {
            this.functionality = functionality;
        }
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public List<FileUploadRequest> getFiles() {
        return files;
    }

    public void setFiles(List<FileUploadRequest> files) {
        this.files = files;
    }

    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }
}