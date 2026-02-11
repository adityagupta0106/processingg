package com.serviceplus.form.validation.executor;

import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public interface ApiExecutor {

    Mono<ResponseEntity<String>> callExternalEndpoint(Class<?> returnObj,
                                                      HttpMethod httpMethod,
                                                      Map<String, String> headers,
                                                      Map<String, Object> params,
                                                      String url,
                                                      String bodyContent,
                                                      MediaType contentType);
}
