package com.serviceplus.form.validation.handlers;
import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_BULK_STATUS_KEY;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.service.BulkFormService;
import com.serviceplus.form.validation.service.PreProcessingFacade;

import reactor.core.publisher.Mono;

@Service
@SanitizeRequest
public class FormSubmissionBulkHandler implements ApplicationFlowHandler {

    private final BulkFormService bulkFormService;

	public FormSubmissionBulkHandler(BulkFormService bulkFormService) {
		this.bulkFormService = bulkFormService;
	}

    @Override
    public String getActivityType() {
        return ACTIVITY_FORM_BULK_STATUS_KEY;
    }

	@Override
	public Mono<ServerResponse> process(String applicationId, ServerRequest request, String statusKey, String txnId,
			Mono<TempTransactionLogs> fetch, ApplicationFlowStatusEntity flow, ServiceMeta service, boolean fromDraft) {

		List<String> applicationIds = Arrays.stream(applicationId.split(",")).map(String::trim)
				.filter(id -> !id.isBlank()).distinct().toList();

		if (applicationIds.isEmpty()) {
			return Mono.error(new SPRuntimeError("Application id is required", HttpStatus.BAD_REQUEST, txnId));
		}

		if (applicationIds.size() == 1) {
			return Mono.error(
					new SPRuntimeError("BULKFS requires multiple application ids", HttpStatus.BAD_REQUEST, txnId));
		}

		return request.bodyToMono(String.class).defaultIfEmpty("").flatMap(appData -> {

			String bulkApplicationIds = String.join(",", applicationIds);

			return bulkFormService.applicationSubmission(request.exchange().getRequest(), txnId, appData,
					bulkApplicationIds, fromDraft, request, flow, service.getServiceId().toString(), service, false);
			
		});
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

        return Mono.error(new SPRuntimeError(
                "BULKFS fetch is not supported",
                HttpStatus.BAD_REQUEST,
                txnId));
    }
}