package com.serviceplus.form.validation.ExceptionHandler;

import org.springframework.http.HttpStatus;

public class SPRuntimeError extends RuntimeException{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private String message;
	private HttpStatus errorCode;
	
	public SPRuntimeError() {
		super();
	}
	
	public SPRuntimeError(String message, HttpStatus badRequest) {
		super();
		this.message = message;
		this.errorCode = badRequest;
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
}
