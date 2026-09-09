package com.serviceplus.form.validation.esb;

import com.serviceplus.form.validation.dto.IntegrationRequest;
import com.serviceplus.form.validation.dto.IntegrationResponse;
import com.serviceplus.form.validation.executor.ApiExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import static com.serviceplus.form.validation.utility.Utility.entityToString;
import static com.serviceplus.form.validation.utility.Utility.stringToEntity;

@Service
public class EsbClient {

    @Value("${splus.esb.service}")
    private String esbService;

    private final ApiExecutor callExternalEndpointService;

    public EsbClient(ApiExecutor callExternalEndpointService) {
        this.callExternalEndpointService = callExternalEndpointService;
    }

    public Mono<IntegrationResponse> invoke(IntegrationRequest request) {

        String body = request == null ? null : entityToString(request);

        String url = esbService.concat("/b/invoke");

        return callExternalEndpointService
                .callExternalEndpoint(
                        IntegrationResponse.class,
                        HttpMethod.POST,
                        buildHeaders(),
                        new HashMap<>(),
                        url,
                        body,
                        MediaType.APPLICATION_JSON
                )
                .flatMap(response -> Mono.just((IntegrationResponse) stringToEntity(response.getBody(), IntegrationResponse.class)))
                .onErrorMap(ex ->
                        new RuntimeException("Failed to invoke ESB", ex)
                );
    }


    private Map<String, String> buildHeaders() {

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }
}