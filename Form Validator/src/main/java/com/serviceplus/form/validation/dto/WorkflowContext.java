package com.serviceplus.form.validation.dto;

import com.serviceplus.form.validation.entity.ProcessingTxn;

public class WorkflowContext {

    private ProcessingTxn txn;

    private ServiceMeta service;

    private String dataId;

	public ProcessingTxn getTxn() {
		return txn;
	}

	public void setTxn(ProcessingTxn txn) {
		this.txn = txn;
	}

	public ServiceMeta getService() {
		return service;
	}

	public void setService(ServiceMeta service) {
		this.service = service;
	}

	public String getDataId() {
		return dataId;
	}

	public void setDataId(String dataId) {
		this.dataId = dataId;
	}

    
}
