package com.serviceplus.form.validation.executor;

import static com.serviceplus.form.validation.utility.Utility.extractServiceName;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.serviceplus.form.validation.CustomAnnotation.CheckCircuitBreakerResponse;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Mono;

@Service
public class AsynchronousApiExecutor  implements ApiExecutor{

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final Logger apiExecutorLogs = LogManager.getLogger("apiExecutorLogger");

    @Override
    @CheckCircuitBreakerResponse(errorMessage = "Failed to fetch due to circuit breaker")
    public Mono<ResponseEntity<String>> callExternalEndpoint(Class<?> returnObj,
                                                             HttpMethod httpMethod,
                                                             Map<String, String> headers,
                                                             Map<String, Object> params,
                                                             String url,
                                                             String bodyContent,
                                                             MediaType contentType) {

        String serviceName = extractServiceName(url);
        String circuitBreakerName = circuitBreakerRegistry.getAllCircuitBreakers().stream()
                .anyMatch(cb -> cb.getName().equals(serviceName)) ? serviceName : "default";

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);

        apiExecutorLogs.info("Calling endpoint {} with params {} body {} header {}",url,params,bodyContent,headers);

        WebClient.RequestBodySpec requestSpec = webClientBuilder.build()
									                .method(httpMethod)
									                .uri(uriBuilder -> {
									                    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
									                    if (params != null) {
									                    	params.forEach((key, value) -> builder.queryParam(key, value));
									                    }
									                    return builder.build().toUri();
									                })
									                .headers(httpHeaders -> {
									                    if (headers != null) headers.forEach(httpHeaders::add);
									                    httpHeaders.setContentType(contentType);
									                });

        //API CALL
        Mono<ResponseEntity<String>> responseMono = (bodyContent != null)
									                ? requestSpec.bodyValue(bodyContent)
									                             .retrieve()
									                             
									                             .toEntity(String.class)
									                : requestSpec.retrieve()
									                             .toEntity(String.class);

        return responseMono
                .timeout(Duration.ofSeconds(150))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(CallNotPermittedException.class, t -> callExternalEndpointFallback(url))
                .onErrorResume(TimeoutException.class, t -> callExternalEndpointFallback(url));

    }

    private Mono<ResponseEntity<String>> callExternalEndpointFallback(String url) {
        String serviceName = extractServiceName(url);
        return Mono.just(ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body(serviceName));
    }

}
