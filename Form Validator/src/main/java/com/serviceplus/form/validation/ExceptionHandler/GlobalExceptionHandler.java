package com.serviceplus.form.validation.ExceptionHandler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import reactor.core.publisher.Mono;

@ControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(value = SPRuntimeError.class)
	public Mono<ResponseEntity<ErrorDetails>> handler(SPRuntimeError  ex){
		ErrorDetails err = new ErrorDetails(ex.getMessage(),ex.getErrorCode(),ex.getErrorCode().value());
		return Mono.just(ResponseEntity.status(ex.getErrorCode()).body(err));
	}
}
