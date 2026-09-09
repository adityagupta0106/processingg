package com.serviceplus.form.validation.dto;

import java.util.List;

public class DocumentGenerationDetails {

    private String taskId;

    private List<DocMappingDTO> documentMapping;

    public static class DocMappingDTO {

        private String referenceId;
        private String documentName;

        private List<LabelValueDTO> action;

        private List<String> documentSource;

        private String digitalSignatureRequired;

        private List<String> viewPermission;

        private List<String> officialViewTasks;

        private LabelValueDTO linkDocumentFromTask;

        private LabelValueDTO linkedDocument;

        private SystemGeneratedDocumentDTO systemGeneratedDocument;

        private List<LabelValueDTO> fileType;

        private Boolean isFileUploadOptional;

        private Boolean isDraftDocumentRequired;

        private String mergeMode;

        private List<MergeOrderDTO> mergeOrder;

        public DocMappingDTO() {
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

        public List<LabelValueDTO> getAction() {
            return action;
        }

        public void setAction(List<LabelValueDTO> action) {
            this.action = action;
        }

        public List<String> getDocumentSource() {
            return documentSource;
        }

        public void setDocumentSource(List<String> documentSource) {
            this.documentSource = documentSource;
        }

        public String getDigitalSignatureRequired() {
            return digitalSignatureRequired;
        }

        public void setDigitalSignatureRequired(String digitalSignatureRequired) {
            this.digitalSignatureRequired = digitalSignatureRequired;
        }

        public List<String> getViewPermission() {
            return viewPermission;
        }

        public void setViewPermission(List<String> viewPermission) {
            this.viewPermission = viewPermission;
        }

        public List<String> getOfficialViewTasks() {
            return officialViewTasks;
        }

        public void setOfficialViewTasks(List<String> officialViewTasks) {
            this.officialViewTasks = officialViewTasks;
        }

        public LabelValueDTO getLinkDocumentFromTask() {
            return linkDocumentFromTask;
        }

        public void setLinkDocumentFromTask(LabelValueDTO linkDocumentFromTask) {
            this.linkDocumentFromTask = linkDocumentFromTask;
        }

        public LabelValueDTO getLinkedDocument() {
            return linkedDocument;
        }

        public void setLinkedDocument(LabelValueDTO linkedDocument) {
            this.linkedDocument = linkedDocument;
        }

        public SystemGeneratedDocumentDTO getSystemGeneratedDocument() {
            return systemGeneratedDocument;
        }

        public void setSystemGeneratedDocument(SystemGeneratedDocumentDTO systemGeneratedDocument) {
            this.systemGeneratedDocument = systemGeneratedDocument;
        }

        public List<LabelValueDTO> getFileType() {
            return fileType;
        }

        public void setFileType(List<LabelValueDTO> fileType) {
            this.fileType = fileType;
        }

        public Boolean getIsFileUploadOptional() {
            return isFileUploadOptional;
        }

        public void setIsFileUploadOptional(Boolean isFileUploadOptional) {
            this.isFileUploadOptional = isFileUploadOptional;
        }

        public Boolean getIsDraftDocumentRequired() {
            return isDraftDocumentRequired;
        }

        public void setIsDraftDocumentRequired(Boolean isDraftDocumentRequired) {
            this.isDraftDocumentRequired = isDraftDocumentRequired;
        }

        public String getMergeMode() {
            return mergeMode;
        }

        public void setMergeMode(String mergeMode) {
            this.mergeMode = mergeMode;
        }

        public List<MergeOrderDTO> getMergeOrder() {
            return mergeOrder;
        }

        public void setMergeOrder(List<MergeOrderDTO> mergeOrder) {
            this.mergeOrder = mergeOrder;
        }

        public static class LabelValueDTO {

            private String label;
            private String value;

            public LabelValueDTO() {
            }

            public String getLabel() {
                return label;
            }

            public void setLabel(String label) {
                this.label = label;
            }

            public String getValue() {
                return value;
            }

            public void setValue(String value) {
                this.value = value;
            }
        }

        public static class SystemGeneratedDocumentDTO {

            private String label;
            private Integer value;

            public SystemGeneratedDocumentDTO() {
            }

            public String getLabel() {
                return label;
            }

            public void setLabel(String label) {
                this.label = label;
            }

            public Integer getValue() {
                return value;
            }

            public void setValue(Integer value) {
                this.value = value;
            }
        }

        public static class MergeOrderDTO {

            private String id;
            private Integer sortOrder;
            private String label;

            public MergeOrderDTO() {
            }

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            public Integer getSortOrder() {
                return sortOrder;
            }

            public void setSortOrder(Integer sortOrder) {
                this.sortOrder = sortOrder;
            }

            public String getLabel() {
                return label;
            }

            public void setLabel(String label) {
                this.label = label;
            }
        }
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public List<DocMappingDTO> getDocumentMapping() {
        return documentMapping;
    }

    public void setDocumentMapping(List<DocMappingDTO> documentMapping) {
        this.documentMapping = documentMapping;
    }
}
