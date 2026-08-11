package com.serviceplus.form.validation.handlers;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.service.NotificationProcessService;
import com.serviceplus.form.validation.utility.ApplicationConstants;

import reactor.core.publisher.Mono;

@Service
@SanitizeRequest
public class NotificationHandler implements ApplicationFlowHandler {

    private final NotificationProcessService notificationProcessService;

    public NotificationHandler(
            NotificationProcessService notificationProcessService) {

        this.notificationProcessService = notificationProcessService;
    }

    @Override
    public String getActivityType() {
        return ApplicationConstants.ACTIVITY_NOTIFICATION_GENERATION;
    }

    @Override
    public Mono<ServerResponse> process(
            String applicationId,
            ServerRequest request,
            String statusKey,
            String txnId,
            Mono<TempTransactionLogs> fetch,
            ApplicationFlowStatusEntity flow,
            ServiceMeta service,
            boolean fromDraft) {

        return notificationProcessService.process(
                applicationId,
                request,
                statusKey,
                txnId,
                fetch,
                flow,
                service,
                fromDraft
        );
    }

    @Override
    public Mono<ServerResponse> fetch(
            String applicationId,
            ServerRequest request,
            String statusKey,
            String txnId,
            Mono<TempTransactionLogs> fetch,
            ApplicationFlowStatusEntity flow,
            ServiceMeta service,
            boolean fromDraft) {

        return notificationProcessService.process(
                applicationId,
                request,
                statusKey,
                txnId,
                fetch,
                flow,
                service,
                fromDraft
        );
    }
}