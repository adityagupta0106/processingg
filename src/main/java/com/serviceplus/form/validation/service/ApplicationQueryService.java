package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ApplicationSearchRequest;
import com.serviceplus.form.validation.dto.Applications;
import com.serviceplus.form.validation.dto.ServerSidePaginationRecord;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.FilterValue;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.repository.ApplicationRepositoryCustom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.*;

import static com.serviceplus.form.validation.utility.ApplicationConstants.APPLICATION_STATUS_DRAFT;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static com.serviceplus.form.validation.utility.Utility.returnError;

@Service
public class ApplicationQueryService {

    private final ApplicationRepositoryCustom repository;

    private final ReactiveApiClient reactiveApiClient;

    private final TransactionGeneration transactionGeneration;

    private final ApplicationFlowRouterRepository applicationFlowRouterRepository;

    private final ApplicationDetailsRepository applicationDetailsRepository;

    @Autowired
    public ApplicationQueryService(ApplicationRepositoryCustom repository, ReactiveApiClient reactiveApiClient, TransactionGeneration transactionGeneration, ApplicationFlowRouterRepository applicationFlowRouterRepository, ApplicationDetailsRepository applicationDetailsRepository) {
        this.repository = repository;
        this.reactiveApiClient = reactiveApiClient;
        this.transactionGeneration = transactionGeneration;
        this.applicationFlowRouterRepository = applicationFlowRouterRepository;
        this.applicationDetailsRepository = applicationDetailsRepository;
    }

    public Mono<ServerSidePaginationRecord<Applications>> search(
            ApplicationSearchRequest request,
            UserSessionObject user) {

        return repository.search(request, user);
    }

    public Mono<ServerResponse> fetchServiceKeyAndTxn(
            ServerRequest request) {

        UserSessionObject user = Objects.requireNonNull(getUserSessionDetails(request.exchange().getRequest()));
        Optional<String> appId = request.queryParam("appId");
        Optional<String> serviceId = request.queryParam("serviceId");

        if (appId.isEmpty() || serviceId.isEmpty()) {
            return Mono.error(new SPRuntimeError("Missing parameters", HttpStatus.BAD_REQUEST, null));
        }

        Integer baseServiceId = Integer.parseInt(serviceId.get()) / 10000;

        return applicationDetailsRepository
                .findByApplicationIdAndBeneficiaryIdAndStatusAndTenantId(
                        appId.get(),
                        user.getUserID(),
                        "S",
                        user.getTenantId()
                )
                .switchIfEmpty(
                        Mono.error(
                                new SPRuntimeError(
                                        "Invalid Application Access",
                                        HttpStatus.UNAUTHORIZED,
                                        null
                                )
                        )
                )
                .flatMap(app ->
                        applicationFlowRouterRepository
                                .findByApplicationIdAndCompletedAndServiceIdAndTenantId(
                                        appId.get(),
                                        0,
                                        Integer.parseInt(serviceId.get()),
                                        user.getTenantId()
                                )
                                .switchIfEmpty(
                                        Mono.error(new SPRuntimeError("Application not found", HttpStatus.BAD_REQUEST, null))
                                )
                                .flatMap(flow ->
                                        reactiveApiClient.fetchServiceKey(
                                                        baseServiceId,
                                                        user,
                                                        appId.get(),
                                                        null,
                                                        Integer.parseInt(serviceId.get()))
                                                .switchIfEmpty(
                                                        Mono.error(new SPRuntimeError("Service metadata not found", HttpStatus.BAD_REQUEST, null))
                                                )
                                                .flatMap(meta ->
                                                        transactionGeneration.createNewTransactionAndUpdateInFlow(
                                                                        meta,
                                                                        flow,
                                                                        request.exchange().getRequest())
                                                                .map(txn -> {

                                                                    Map<String, String> response = new HashMap<>();

                                                                    response.put("serviceKey", meta.getServiceKey());
                                                                    response.put("txnId", txn.getTxnId());

                                                                    return response;
                                                                }))
                                                .flatMap(ServerResponse.ok()::bodyValue))
                );
    }
}
