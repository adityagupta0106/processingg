package com.serviceplus.form.validation.CustomAnnotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SanitizeRequest {
}

