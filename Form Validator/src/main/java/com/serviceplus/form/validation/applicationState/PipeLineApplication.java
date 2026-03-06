package com.serviceplus.form.validation.applicationState;

import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.FilterValue;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.service.IApplicationManagerService;
import com.serviceplus.form.validation.service.PreProcessingFacade;
import com.serviceplus.form.validation.service.PreProcessingService;
import com.serviceplus.form.validation.utility.PathRouter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

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
        return null;
    }
}
