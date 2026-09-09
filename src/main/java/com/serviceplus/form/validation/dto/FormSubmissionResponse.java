package com.serviceplus.form.validation.dto;

import com.serviceplus.form.validation.entity.TempTransactionLogs;

public class FormSubmissionResponse {

    private String txnId;

    private ServiceMeta service;
    
    private TempTransactionLogs tempTransactionLogs;

    private String appData;

    private String responseBody;

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

	public String getAppData() {
		return appData;
	}

	public void setAppData(String appData) {
		this.appData = appData;
	}
	
	public TempTransactionLogs getTempTransactionLogs() {
		return tempTransactionLogs;
	}

	public void setTempTransactionLogs(TempTransactionLogs tempTransactionLogs) {
		this.tempTransactionLogs = tempTransactionLogs;
	}

	public String getResponseBody() {
		return responseBody;
	}

	public void setResponseBody(String responseBody) {
		this.responseBody = responseBody;
	}
    
}
