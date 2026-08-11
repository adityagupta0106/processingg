package com.serviceplus.form.validation.service;


import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.Helpers.SystemAttributeHelper;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import com.serviceplus.form.validation.flow.EventDecider;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;

import reactor.core.publisher.Mono;

@Service
public class NotificationProcessService {

	private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

	private final ReactiveApiClient reactiveApiClient;
	private final ProcessingTxnRepository processingTxnRepository;
	private final EventDecider eventDecider;
	private final ActivityMapService activityMapService;
	private final ApplicationDetailsRepository applicationDetailsRepository;
	private final SystemAttributeHelper systemAttributeHelper;

	public NotificationProcessService(ReactiveApiClient reactiveApiClient,
			ProcessingTxnRepository processingTxnRepository, EventDecider eventDecider, ActivityMapService activityMapService, ApplicationDetailsRepository applicationDetailsRepository, SystemAttributeHelper systemAttributeHelper) {

		this.reactiveApiClient = reactiveApiClient;
		this.processingTxnRepository = processingTxnRepository;
		this.eventDecider = eventDecider;
		this.activityMapService = activityMapService;
		this.applicationDetailsRepository = applicationDetailsRepository;
		this.systemAttributeHelper = systemAttributeHelper;
	}

	public Mono<ServerResponse> process(String applicationId, ServerRequest request, String statusKey, String txnId,
			Mono<TempTransactionLogs> fetch, ApplicationFlowStatusEntity flow, ServiceMeta service, boolean fromDraft) {

		UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());

		Mono<ApplicationDetails> applicationDetails = applicationDetailsRepository
				.findByApplicationIdAndTenantId(applicationId, user.getTenantId());
		Map<String, Object> systemAttrMap=new HashMap<>();
		return applicationDetails.doOnNext(details -> systemAttributeHelper.systemAttrMap(systemAttrMap,details, service))
				.then(activityMapService
						.activityConfigId(service, user, applicationId, flow.getTxnId(), flow.getActivityType())
						.switchIfEmpty(Mono.error(new SPRuntimeError("Notification activity configuration not found.",
								HttpStatus.BAD_REQUEST, flow.getTxnId())))
						.flatMap(activityConfigId -> reactiveApiClient.sendNotification(applicationId,
								service.getServiceId(), activityConfigId, flow.getTxnId(),systemAttrMap, request))
						.then(processingTxnRepository.findById(flow.getTxnId())
								.switchIfEmpty(Mono.error(new SPRuntimeError("Processing transaction not found.",
										HttpStatus.INTERNAL_SERVER_ERROR, flow.getTxnId()))))
						.flatMap(txnLog -> {

							applicationFlowLogs.info(
									"TxnId : {} | Notification processing completed successfully. Invoking EventDecider for next workflow activity.",
									flow.getTxnId());

							return eventDecider.proceedToNext(flow.getDataId(), service, user, txnLog, applicationId,
									null, flow.getActivityType(), request, flow);
						}));
	}
}
