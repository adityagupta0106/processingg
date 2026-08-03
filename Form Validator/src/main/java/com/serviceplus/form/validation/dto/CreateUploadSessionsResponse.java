package com.serviceplus.form.validation.dto;

import java.util.List;

public class CreateUploadSessionsResponse {

    private List<UploadSessionItem> uploads;

    public CreateUploadSessionsResponse(){}

    public CreateUploadSessionsResponse(List<UploadSessionItem> uploads) {
        this.uploads = uploads;
    }

    public static class UploadSessionItem {
        private String uploadId;
        private String uploadToken;
        private String category;
        private Long expiresIn;
        private String referenceId;
        private Long maxChunks;
        private Long chunkSizeBytes;

        public UploadSessionItem(){}

        public UploadSessionItem(String uploadId, String uploadToken, String category, long expiresIn, String referenceId,Long maxChunks,Long chunkSizeBytes) {
            this.uploadId = uploadId;
            this.uploadToken = uploadToken;
            this.category = category;
            this.expiresIn = expiresIn;
            this.referenceId = referenceId;
            this.maxChunks = maxChunks;
            this.chunkSizeBytes = chunkSizeBytes;
        }

        public Long getChunkSizeBytes() {
            return chunkSizeBytes;
        }

        public void setChunkSizeBytes(Long chunkSizeBytes) {
            this.chunkSizeBytes = chunkSizeBytes;
        }

        public String getUploadId() {
            return uploadId;
        }

        public void setUploadId(String uploadId) {
            this.uploadId = uploadId;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getUploadToken() {
            return uploadToken;
        }

        public void setUploadToken(String uploadToken) {
            this.uploadToken = uploadToken;
        }

        public long getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(long expiresIn) {
            this.expiresIn = expiresIn;
        }

        public String getReferenceId() {
            return referenceId;
        }

        public void setReferenceId(String referenceId) {
            this.referenceId = referenceId;
        }

        public void setExpiresIn(Long expiresIn) {
            this.expiresIn = expiresIn;
        }

        public Long getMaxChunks() {
            return maxChunks;
        }

        public void setMaxChunks(Long maxChunks) {
            this.maxChunks = maxChunks;
        }
    }

    public List<UploadSessionItem> getUploads() {
        return uploads;
    }

    public void setUploads(List<UploadSessionItem> uploads) {
        this.uploads = uploads;
    }
}