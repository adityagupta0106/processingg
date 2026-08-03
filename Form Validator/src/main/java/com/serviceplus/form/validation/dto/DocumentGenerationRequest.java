package com.serviceplus.form.validation.dto;

import java.util.List;

public class DocumentGenerationRequest {

    private List<DocumentSectionRequest> documentSections;

    public static class DocumentSectionRequest {

        private Boolean merged;

        private String mergedDocumentId;

        private List<DocumentRequest> documents;

        public static class DocumentRequest {

            private String documentId;

            private String referenceId;

            private String sourceType;

            public String getDocumentId() {
                return documentId;
            }

            public void setDocumentId(String documentId) {
                this.documentId = documentId;
            }

            public String getReferenceId() {
                return referenceId;
            }

            public void setReferenceId(String referenceId) {
                this.referenceId = referenceId;
            }

            public String getSourceType() {
                return sourceType;
            }

            public void setSourceType(String sourceType) {
                this.sourceType = sourceType;
            }
        }

        public List<DocumentRequest> getDocuments() {
            return documents;
        }

        public void setDocuments(List<DocumentRequest> documents) {
            this.documents = documents;
        }

        public String getMergedDocumentId() {
            return mergedDocumentId;
        }

        public void setMergedDocumentId(String mergedDocumentId) {
            this.mergedDocumentId = mergedDocumentId;
        }

        public Boolean getMerged() {
            return merged;
        }

        public void setMerged(Boolean merged) {
            this.merged = merged;
        }
    }

    public List<DocumentSectionRequest> getDocumentSections() {
        return documentSections;
    }

    public void setDocumentSections(List<DocumentSectionRequest> documentSections) {
        this.documentSections = documentSections;
    }
}