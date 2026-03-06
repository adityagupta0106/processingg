package com.serviceplus.form.validation.entity;

import com.serviceplus.form.validation.dto.ServiceMeta;

import java.util.Date;

public class TempTransactionLogs {

    private String txnId;
    private ServiceMeta service;
    private String userIp;
    private Date startTime;
    private Integer userId;

    public TempTransactionLogs(){
        this.startTime = new Date();
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public ServiceMeta getService() {
        return service;
    }

    public void setService(ServiceMeta service) {
        this.service = service;
    }

    public String getUserIp() {
        return userIp;
    }

    public void setUserIp(String userIp) {
        this.userIp = userIp;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }
}
