package com.serviceplus.form.validation.controller;

import com.serviceplus.form.validation.applicationState.ApplicationManager;
import com.serviceplus.form.validation.applicationState.ApplicationStateFactory;
import com.serviceplus.form.validation.applicationState.IncompleteApplication;
import com.serviceplus.form.validation.applicationState.PipeLineApplication;
import com.serviceplus.form.validation.utility.ApplicationConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.serviceplus.form.validation.utility.ApplicationConstants.APPLICATION_STATUS_DRAFT;

@RestController
public class ApplicationFetchController {

    @Autowired
    private ApplicationStateFactory applicationStateFactory;

    public Mono<ServerResponse> getApplicationList(ServerRequest request) {
        Optional<String> oappState = request.queryParam("appState");

        String appState = oappState.orElse("");

        ApplicationManager applicationManager = applicationStateFactory.getManager(appState);
        return applicationManager.fetchList(request);
    }

    public Mono<ServerResponse> loadApplicationAndFetchServiceKey(ServerRequest request) {
        Optional<String> oappState = request.queryParam("appState");

        String appState = oappState.orElse("");

        ApplicationManager applicationManager = applicationStateFactory.getManager(appState);
        return applicationManager.loadApplicationAndFetchServiceKey(request);
    }

}
