package com.serviceplus.form.validation.entity;

import com.serviceplus.form.validation.dto.Services;

import java.util.Date;

public class TempTransactionLogs {

    private String txnId;
    private Services service;
    private String userIp;
    private Date startTime;

    public TempTransactionLogs(){
        this.startTime = new Date();
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public Services getService() {
        return service;
    }

    public void setService(Services service) {
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
}
