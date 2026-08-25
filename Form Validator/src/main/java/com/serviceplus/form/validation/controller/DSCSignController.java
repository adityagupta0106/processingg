package com.serviceplus.form.validation.controller;

import static com.serviceplus.form.validation.utility.Utility.decryptServiceKeys;
import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.DSCSignRequest;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.service.DSCSignService;

import reactor.core.publisher.Mono;

@RestController
public class DSCSignController {

    private final DSCSignService dscSignService;
    private final ApplicationFlowRouterRepository applicationFlowRouterRepository;

	public DSCSignController(DSCSignService dscSignService,
			ApplicationFlowRouterRepository applicationFlowRouterRepository) {

		this.dscSignService = dscSignService;
		this.applicationFlowRouterRepository = applicationFlowRouterRepository;
	}

	public Mono<ServerResponse> sign(ServerRequest request) {

		Optional<String> appIdOpt = request.queryParam("appId");

		Optional<String> serviceKey = request.queryParam("serviceKey");

		Optional<String> txnIdOpt = request.queryParam("txnId");

		if (serviceKey.isEmpty() || appIdOpt.isEmpty() || txnIdOpt.isEmpty()) {

			return Mono.error(new SPRuntimeError("Mandatory parameters is required", HttpStatus.BAD_REQUEST, null));
		}

		ServiceMeta service = decryptServiceKeys(serviceKey.get());

		UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());

		if (user == null) {

			return Mono.error(new SPRuntimeError("Invalid session", HttpStatus.UNAUTHORIZED, null));
		}

		String appId = appIdOpt.get();
		String txnId = txnIdOpt.get();

		return applicationFlowRouterRepository
				.findByApplicationIdAndTxnIdAndCompletedAndTaskIdAndServiceIdAndTenantId(appId, txnId, 0,
						service.getTaskId(), service.getServiceId(), user.getTenantId())

				.switchIfEmpty(
						Mono.error(new SPRuntimeError("Current workflow task not found", HttpStatus.NOT_FOUND, txnId)))

				.flatMap(flow ->

				request.bodyToMono(DSCSignRequest.class)

						.switchIfEmpty(Mono
								.error(new SPRuntimeError("Request body is required", HttpStatus.BAD_REQUEST, txnId)))

						.flatMap(signRequest ->

						dscSignService.sign(user, flow, service, signRequest, txnId)))

				.flatMap(response -> ServerResponse.ok().bodyValue(response))

				.onErrorMap(ex -> {

					if (ex instanceof SPRuntimeError) {
						return ex;
					}

					ex.printStackTrace();

					return new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR, txnId);
				});
	}
    
    
}
