package com.serviceplus.form.validation.service;

import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

public interface ApiExecutor {

	Object callExternalEndpoint(Class<?> returnObj,
										            HttpMethod httpMethod,
										            Map<String, String> headers,
										            Map<String, Object> params,
										            String url,
										            String bodyContent,
										            MediaType contentType);
}
