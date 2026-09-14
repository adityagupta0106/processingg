package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApplicationApplicantTrackingResponse {

    private ApplicantTrackingStatistics statistics;

    private List<ApplicantApplicationDTO> applications = new ArrayList<>();

    private long totalElements;

    private int totalPages;

    private int page;

    private int size;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApplicantTrackingStatistics {

        private long totalSubmissions;

        private long requiresResponse;

        private long delivered;

        private long inProgress;


        public long getTotalSubmissions() {
            return totalSubmissions;
        }

        public void setTotalSubmissions(long totalSubmissions) {
            this.totalSubmissions = totalSubmissions;
        }

        public long getRequiresResponse() {
            return requiresResponse;
        }

        public void setRequiresResponse(long requiresResponse) {
            this.requiresResponse = requiresResponse;
        }

        public long getDelivered() {
            return delivered;
        }

        public void setDelivered(long delivered) {
            this.delivered = delivered;
        }

        public long getInProgress() {
            return inProgress;
        }

        public void setInProgress(long inProgress) {
            this.inProgress = inProgress;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApplicantApplicationDTO {

        private String applId;

        private String applRefNo;

        private Integer serviceId;

        private String serviceName;

        private String beneficiaryName;
        private String receivedDate;

        private String taskName;

        private String taskId;

        private String formId;

        private boolean pendingApplicantAction;

        private InboxActionDTO inboxAction;

        @JsonIgnore
        private String taskType;

        public Integer getServiceId() {
            return serviceId;
        }

        public void setServiceId(Integer serviceId) {
            this.serviceId = serviceId;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getBeneficiaryName() {
            return beneficiaryName;
        }

        public void setBeneficiaryName(String beneficiaryName) {
            this.beneficiaryName = beneficiaryName;
        }

        public String getTaskName() {
            return taskName;
        }

        public void setTaskName(String taskName) {
            this.taskName = taskName;
        }

        public boolean isPendingApplicantAction() {
            return pendingApplicantAction;
        }

        public void setPendingApplicantAction(boolean pendingApplicantAction) {
            this.pendingApplicantAction = pendingApplicantAction;
        }

        public InboxActionDTO getInboxAction() {
            return inboxAction;
        }

        public void setInboxAction(InboxActionDTO inboxAction) {
            this.inboxAction = inboxAction;
        }

        public String getApplId() {
            return applId;
        }

        public void setApplId(String applId) {
            this.applId = applId;
        }

        public String getReceivedDate() {
            return receivedDate;
        }

        public void setReceivedDate(String receivedDate) {
            this.receivedDate = receivedDate;
        }

        public String getApplRefNo() {
            return applRefNo;
        }

        public void setApplRefNo(String applRefNo) {
            this.applRefNo = applRefNo;
        }

        public String getTaskType() {
            return taskType;
        }

        public void setTaskType(String taskType) {
            this.taskType = taskType;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getFormId() {
            return formId;
        }

        public void setFormId(String formId) {
            this.formId = formId;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InboxActionDTO {

        private Long inboxId;

        private String currentProcessId;

        private String taskId;

        private String taskName;

        private String formId;

        private String tenantId;

        private boolean requiresPayment;

        private boolean requiresForm;

        private String status;


        public Long getInboxId() {
            return inboxId;
        }

        public void setInboxId(Long inboxId) {
            this.inboxId = inboxId;
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

        public String getTaskName() {
            return taskName;
        }

        public void setTaskName(String taskName) {
            this.taskName = taskName;
        }

        public String getFormId() {
            return formId;
        }

        public void setFormId(String formId) {
            this.formId = formId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public boolean isRequiresPayment() {
            return requiresPayment;
        }

        public void setRequiresPayment(boolean requiresPayment) {
            this.requiresPayment = requiresPayment;
        }

        public boolean isRequiresForm() {
            return requiresForm;
        }

        public void setRequiresForm(boolean requiresForm) {
            this.requiresForm = requiresForm;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }


    public ApplicantTrackingStatistics getStatistics() {
        return statistics;
    }

    public void setStatistics(ApplicantTrackingStatistics statistics) {
        this.statistics = statistics;
    }

    public List<ApplicantApplicationDTO> getApplications() {
        return applications;
    }

    public void setApplications(List<ApplicantApplicationDTO> applications) {
        this.applications = applications;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}