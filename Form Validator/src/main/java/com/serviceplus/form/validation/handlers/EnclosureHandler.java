package com.serviceplus.form.validation.handlers;

import com.serviceplus.form.validation.dto.HandlerResponse;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_ENCLOSURE_STATUS_KEY;

@Service
public class EnclosureHandler implements ApplicationFlowHandler{

    @Override
    public Mono<ServerResponse> process(String applicationId, ServerRequest request, String statusKey, String txnId, Mono<TempTransactionLogs> fetch
                                    , ApplicationFlowStatusEntity flow, ServiceMeta service, boolean fromDraft) {
        HandlerResponse hr = new HandlerResponse();
        hr.setData(Map.of("E1", "Enclosure data"));
        hr.setTxnId(txnId);
        hr.setApplicationId(applicationId);
        hr.setActivityType(ACTIVITY_ENCLOSURE_STATUS_KEY);
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(hr);
    }

    @Override
    public Mono<ServerResponse> fetch(String applicationId, ServerRequest request, String statusKey, String txnId, Mono<TempTransactionLogs> fetch,
                                      ApplicationFlowStatusEntity flow, ServiceMeta service, boolean fromDraft) {
        HandlerResponse hr = new HandlerResponse();
        hr.setData(Map.of("E1", "Enclosure data"));
        hr.setTxnId(txnId);
        hr.setApplicationId(applicationId);
        hr.setActivityType(ACTIVITY_ENCLOSURE_STATUS_KEY);
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                .bodyValue(hr);
    }
}
