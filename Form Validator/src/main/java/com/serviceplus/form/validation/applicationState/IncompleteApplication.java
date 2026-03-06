package com.serviceplus.form.validation.applicationState;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.Applications;
import com.serviceplus.form.validation.dto.HandlerResponse;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.FilterValue;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.service.IApplicationManagerService;
import com.serviceplus.form.validation.service.PreProcessingService;
import com.serviceplus.form.validation.service.ReactiveApiClient;
import com.serviceplus.form.validation.service.TransactionGeneration;
import com.serviceplus.form.validation.utility.Utility;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.serviceplus.form.validation.utility.ApplicationConstants.APPLICATION_STATUS_DRAFT;
import static com.serviceplus.form.validation.utility.Utility.*;

@Service
@SanitizeRequest
public class IncompleteApplication implements ApplicationManager{

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    @Autowired
    private IApplicationManagerService applicationManagerService;

    @Autowired
    private ReactiveApiClient reactiveApiClient;

    @Autowired
    private ApplicationFlowRouterRepository applicationFlowRouterRepository;

    @Autowired
    private TransactionGeneration transactionGeneration;

    @Override
    public Mono<ServerResponse> fetchList(ServerRequest request) {
        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
        Optional<String> oOffSet = request.queryParam("offSet");

        Integer offSet = oOffSet.map(Integer::parseInt).orElse(0);

        Map<String, Object> params = new java.util.HashMap<>(Map.of("beneficiary_id", user.getUserID()));
        params.put("status",
                new FilterValue(
                        List.of("S"),
                        FilterValue.Operator.IN
                )
        );

        return applicationManagerService.fetch(
                        user.getUserID(),APPLICATION_STATUS_DRAFT,offSet,List.of(),null,null,user,params,"apply_date"
                )
                .flatMap(response -> ServerResponse.ok().bodyValue(response))
                .onErrorResume(Exception.class , ex -> returnError(ex,"",applicationFlowLogs));
    }

    @Override
    public Mono<ServerResponse> loadApplicationAndFetchServiceKey(ServerRequest request) {
        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
        Optional<String> appId = request.queryParam("appId");
        Optional<String> serviceId = request.queryParam("serviceId");

        if (appId.isEmpty() || serviceId.isEmpty()) {
            return Mono.error(new SPRuntimeError("Missing parameters", HttpStatus.BAD_REQUEST, null));
        }

        Map<String, Object> params = new java.util.HashMap<>(Map.of("application_id", appId.get()));
        params.put("status",
                new FilterValue(
                        List.of("S"),
                        FilterValue.Operator.IN
                )
        );

        return applicationManagerService.fetch(
                        user.getUserID(), APPLICATION_STATUS_DRAFT, -1, List.of(), null, null, user, params,"apply_date"
                )
                .flatMap(_ ->
                        applicationFlowRouterRepository.findByApplicationIdAndCompletedAndServiceIdAndTenantId(
                                        appId.get(), 0, Integer.parseInt(serviceId.get()), user.getTenantId()
                                )
                                .switchIfEmpty(
                                        Mono.error(new SPRuntimeError("Invalid Access / Details not found [01]", HttpStatus.BAD_REQUEST, null))
                                )
                                .flatMap(
                                        flow -> {
                                            String taskId = flow.getTaskId();
                                            Integer bSID = Integer.parseInt(serviceId.get()) / 10000;
                                            return reactiveApiClient
                                                    .fetchServiceKey(bSID, user, appId.get(), taskId, Integer.parseInt(serviceId.get())
                                                    )
                                                    .switchIfEmpty(
                                                            Mono.error(new SPRuntimeError("Invalid Access / Details not found [02]", HttpStatus.BAD_REQUEST, null))
                                                    )
                                                    .flatMap(
                                                            meta -> transactionGeneration.createNewTransactionAndUpdateInFlow(
                                                                    meta, flow, request.exchange().getRequest()
                                                                    )
                                                                    .flatMap(processingTxn -> {
                                                                        Map<String, String> map = new HashMap<>();
                                                                        map.put("serviceKey", meta.getServiceKey());
                                                                        map.put("txnId", processingTxn.getTxnId());
                                                                        return ServerResponse.ok().bodyValue(map);
                                                                    })
                                                    );
                                        }
                                )

                )
                .onErrorResume(Exception.class, ex -> returnError(ex, "", applicationFlowLogs));
    }
}
