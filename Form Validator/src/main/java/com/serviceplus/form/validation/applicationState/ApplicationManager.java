package com.serviceplus.form.validation.applicationState;

import com.serviceplus.form.validation.dto.Applications;
import com.serviceplus.form.validation.dto.HandlerResponse;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ApplicationManager{

    Mono<ServerResponse> fetchList(ServerRequest request);

    Mono<ServerResponse> loadApplicationAndFetchServiceKey(ServerRequest request);
}
