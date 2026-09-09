package com.serviceplus.form.validation.dto;

import java.util.ArrayList;
import java.util.List;

public class DocumentSectionResponse {

    private String referenceId;

    private String documentName;

    private Boolean mergeRequired;

    private List<ResolvedDocument> documents = new ArrayList<>();

    private MergedDocumentResponse mergedDocument;

    public static class MergedDocumentResponse {

        private String documentId;
        private String documentName;
        private String downloadUrl;

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public void setDownloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
        }

        public String getDocumentName() {
            return documentName;
        }

        public void setDocumentName(String documentName) {
            this.documentName = documentName;
        }

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }
    }

    public List<ResolvedDocument> getDocuments() {
        return documents;
    }

    public void setDocuments(List<ResolvedDocument> documents) {
        this.documents = documents;
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

    public Boolean getMergeRequired() {
        return mergeRequired;
    }

    public void setMergeRequired(Boolean mergeRequired) {
        this.mergeRequired = mergeRequired;
    }

    public MergedDocumentResponse getMergedDocument() {
        return mergedDocument;
    }

    public void setMergedDocument(MergedDocumentResponse mergedDocument) {
        this.mergedDocument = mergedDocument;
    }
}
