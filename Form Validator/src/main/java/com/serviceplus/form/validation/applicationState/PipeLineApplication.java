package com.serviceplus.form.validation.applicationState;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.FilterValue;
import com.serviceplus.form.validation.flow.EventRouter;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.service.IApplicationManagerService;
import com.serviceplus.form.validation.service.PreProcessingFacade;
import com.serviceplus.form.validation.service.PreProcessingService;
import com.serviceplus.form.validation.utility.PathRouter;
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
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;
import static com.serviceplus.form.validation.utility.Utility.returnError;

@Service
public class PipeLineApplication implements ApplicationManager{

    @Autowired
    private IApplicationManagerService applicationManager;

    @Autowired
    private IApplicationManagerService applicationManagerService;

    @Autowired
    private EventRouter applicationFlowRouter;

    @Autowired
    private ApplicationFlowRouterRepository applicationFlowRouterRepository;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    @Override
    public Mono<ServerResponse> fetchList(ServerRequest request) {
        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
        Optional<String> oOffSet = request.queryParam("offSet");

        Integer offSet = oOffSet.map(Integer::parseInt).orElse(0);

        Map<String, Object> params = new java.util.HashMap<>(Map.of("beneficiary_id", user.getUserID()));
        params.put("status",
                new FilterValue(
                        List.of("S"),
                        FilterValue.Operator.NOT_IN
                )
        );

        return applicationManager.fetch(
                        user.getUserID(),"",offSet, List.of(),null,null,user,params,"apply_date"
                )
                .flatMap(response -> ServerResponse.ok().bodyValue(response))
                .onErrorResume(Exception.class , ex -> returnError(ex,"",applicationFlowLogs));
    }

    @Override
   public Mono<ServerResponse> loadApplicationAndFetchServiceKey(ServerRequest request) {
//        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());
//        Optional<String> appId = request.queryParam("appId");
//        Optional<String> serviceId = request.queryParam("serviceId");
//
//        if (appId.isEmpty() || serviceId.isEmpty()) {
//            return Mono.error(new SPRuntimeError("Missing parameters", HttpStatus.BAD_REQUEST, null));
//        }
//
//        Map<String, Object> params = new java.util.HashMap<>(Map.of("application_id", appId.get()));
//        params.put("status",
//                new FilterValue(
//                        List.of("S","D","R"),
//                        FilterValue.Operator.NOT_IN
//                )
//        );
//
//        return applicationManagerService.fetch(
//                        user.getUserID(), "", -1, List.of(), null, null, user, params,"apply_date"
//                )
//                .flatMap(_ ->
//                        applicationFlowRouterRepository.findByApplicationIdAndCompletedAndServiceIdAndTenantId(
//                                        appId.get(), 0, Integer.parseInt(serviceId.get()), user.getTenantId()
//                                )
//                                .switchIfEmpty(
//                                        Mono.error(new SPRuntimeError("Invalid Access / Details not found [01]", HttpStatus.BAD_REQUEST, null))
//                                )
//                                .flatMap(
//                                        flow -> {
//
//                                            ServiceMeta serviceMeta = new ServiceMeta();
//                                            serviceMeta.setServiceId(flow.getServiceId());
//                                            serviceMeta.setFormId(flow.getFormId());
//                                            serviceMeta.setTaskId(flow.getTaskId());
//
//                                            return applicationFlowRouter.next(
//                                                    flow.getActivityType(),
//                                                    flow.getApplicationId(),
//                                                    request,
//                                                    Mono.empty(),
//                                                    flow,
//                                                    serviceMeta,
//                                                    flow.getTxnId(),
//                                                    true,
//                                                    user.getUserID()
//                                            );
//                                        }
//                                )
//                )
//                .onErrorResume(Exception.class, ex -> returnError(ex, "", applicationFlowLogs));
    return null;}
}
