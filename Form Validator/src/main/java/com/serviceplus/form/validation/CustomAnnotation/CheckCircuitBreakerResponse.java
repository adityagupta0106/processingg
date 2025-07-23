package com.serviceplus.form.validation.CustomAnnotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CheckCircuitBreakerResponse {
    String errorMessage() default "Circuit breaker fallback triggered";
}
