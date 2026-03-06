package com.serviceplus.form.validation.ExceptionHandler;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDetails {

	private String message;
	private HttpStatus status;
	private Integer errorCode;
	private Map<String,Object> data;
    private String txnId;

	public ErrorDetails() {
	}
	
	public ErrorDetails(String message, HttpStatus status, Integer errorCode,String txnId) {
		super();
		this.message = message;
		this.status = status;
		this.errorCode = errorCode;
        this.txnId = txnId;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public void setStatus(HttpStatus status) {
		this.status = status;
	}

	public Integer getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(Integer errorCode) {
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
