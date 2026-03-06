package com.serviceplus.form.validation.ExceptionHandler;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class SPRuntimeError extends RuntimeException{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private String message;
	private HttpStatus errorCode;
    private Map<String,Object> data;
    private String txnId;
	
	public SPRuntimeError() {
		super();
	}
	
	public SPRuntimeError(String message, HttpStatus badRequest,String txnId) {
		super();
		this.message = message;
		this.errorCode = badRequest;
        this.txnId = txnId;
	}
	
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public HttpStatus getErrorCode() {
		return errorCode;
	}
	public void setErrorCode(HttpStatus errorCode) {
		this.errorCode = errorCode;
	}

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }
}
