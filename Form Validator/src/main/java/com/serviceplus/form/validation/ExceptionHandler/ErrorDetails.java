package com.serviceplus.form.validation.ExceptionHandler;

import org.springframework.http.HttpStatus;

public class ErrorDetails {

	private String message;
	private HttpStatus status;
	private Integer errorCode;
	
	public ErrorDetails() {
	}
	
	public ErrorDetails(String message, HttpStatus status, Integer errorCode) {
		super();
		this.message = message;
		this.status = status;
		this.errorCode = errorCode;
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

    
	
	
}
