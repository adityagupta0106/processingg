package com.serviceplus.form.validation.auaVerification.dto;

public class AuaOtpRequest {

    private Integer serviceId;
    private String taskId;
    private String txnId;
    private String aadhaar;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public String getAadhaar() {
        return aadhaar;
    }

    public void setAadhaar(String aadhaar) {
        this.aadhaar = aadhaar;
    }
}
