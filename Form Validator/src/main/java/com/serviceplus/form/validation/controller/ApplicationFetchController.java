package com.serviceplus.form.validation.controller;

import com.serviceplus.form.validation.applicationState.ApplicationManager;
import com.serviceplus.form.validation.applicationState.ApplicationStateFactory;
import com.serviceplus.form.validation.applicationState.IncompleteApplication;
import com.serviceplus.form.validation.applicationState.PipeLineApplication;
import com.serviceplus.form.validation.dto.ApplicationSearchRequest;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.service.ApplicationQueryService;
import com.serviceplus.form.validation.utility.ApplicationConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.serviceplus.form.validation.utility.ApplicationConstants.APPLICATION_STATUS_DRAFT;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

@RestController
public class ApplicationFetchController {

    @Autowired
    private ApplicationStateFactory applicationStateFactory;

    private final ApplicationQueryService applicationQueryService;

    @Autowired
    public ApplicationFetchController(ApplicationQueryService applicationQueryService) {
        this.applicationQueryService = applicationQueryService;
    }

    public Mono<ServerResponse> loadApplicationAndFetchServiceKey(ServerRequest request) {
        return applicationQueryService.fetchServiceKeyAndTxn(request);
    }

    public Mono<ServerResponse> fetchApplications(ServerRequest request) {

        return request.bodyToMono(ApplicationSearchRequest.class)
                .flatMap(searchRequest -> {

                    searchRequest.setState("DRAFT");
                    UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
                    return applicationQueryService.search(searchRequest, user);
                })
                .flatMap(ServerResponse.ok()::bodyValue);
    }

}
