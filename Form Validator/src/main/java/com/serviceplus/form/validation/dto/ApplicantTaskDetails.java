package com.serviceplus.form.validation.dto;

public class ApplicantTaskDetails {

    private String currentProcessId;

    private String taskId;

    private String applicationId;

    private boolean submissionToSameOfficial;

    private boolean uploadRejectedEnclosures;

    private boolean deoSubmit;

    private boolean requiresForm;

    private boolean requiresPayment;

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getCurrentProcessId() {
        return currentProcessId;
    }

    public void setCurrentProcessId(String currentProcessId) {
        this.currentProcessId = currentProcessId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public boolean isSubmissionToSameOfficial() {
        return submissionToSameOfficial;
    }

    public void setSubmissionToSameOfficial(boolean submissionToSameOfficial) {
        this.submissionToSameOfficial = submissionToSameOfficial;
    }

    public boolean isUploadRejectedEnclosures() {
        return uploadRejectedEnclosures;
    }

    public void setUploadRejectedEnclosures(boolean uploadRejectedEnclosures) {
        this.uploadRejectedEnclosures = uploadRejectedEnclosures;
    }

    public boolean isDeoSubmit() {
        return deoSubmit;
    }

    public void setDeoSubmit(boolean deoSubmit) {
        this.deoSubmit = deoSubmit;
    }

    public boolean isRequiresForm() {
        return requiresForm;
    }

    public void setRequiresForm(boolean requiresForm) {
        this.requiresForm = requiresForm;
    }

    public boolean isRequiresPayment() {
        return requiresPayment;
    }

    public void setRequiresPayment(boolean requiresPayment) {
        this.requiresPayment = requiresPayment;
    }
}
