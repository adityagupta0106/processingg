package com.serviceplus.form.validation.ExceptionHandler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import reactor.core.publisher.Mono;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = SPRuntimeError.class)
    public Mono<ResponseEntity<ErrorDetails>> handler(SPRuntimeError ex) {
        ErrorDetails err = new ErrorDetails(
                ex.getMessage(),
                ex.getErrorCode(),
                ex.getErrorCode().value(),
                ex.getTxnId()
        );
        err.setData(ex.getData());
        return Mono.just(ResponseEntity
                .status(ex.getErrorCode())
                .body(err));
    }

    @ExceptionHandler(value = Exception.class)
    public Mono<ResponseEntity<ErrorDetails>> global(Exception ex) {
        ex.printStackTrace();
        ErrorDetails err = new ErrorDetails(
                ex.getLocalizedMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                null
        );

        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(err));
    }
}

