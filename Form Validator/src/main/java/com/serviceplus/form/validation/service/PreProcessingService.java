package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.Utility.getUserSessionDetails;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.DocumentGenerationRequest;
import com.serviceplus.form.validation.dto.InboxApplReqDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;

import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Service
@SanitizeRequest
public class PreProcessingService {

	@Autowired
	private PreProcessingFacade preProcessingFacade;

	public Mono<ServerResponse> serviceList(ServerHttpRequest request) {
		try {
			UserSessionObject user = getUserSessionDetails(request);

			Mono<ServerResponse> map = preProcessingFacade.getServiceList(user)
					.flatMap(response -> ServerResponse.ok().bodyValue(response));

			return map;

		} catch (Exception e) {
			e.printStackTrace();
			return Mono.error(new SPRuntimeError("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR, ""));
		}
	}

	public Mono<ServerResponse> apply(ServerRequest fluxRequest) {
		try {

			Optional<String> applyKeyOpt = fluxRequest.queryParam("serviceKey");
			Optional<String> serviceIdOpt = fluxRequest.queryParam("serviceId");

			if (applyKeyOpt.isEmpty() || serviceIdOpt.isEmpty()) {
				return Mono.error(new SPRuntimeError("Parameters missing", HttpStatus.BAD_REQUEST, null));
			}

			String applyKey = applyKeyOpt.get();
			String serviceId = serviceIdOpt.get();

			ServerHttpRequest request = fluxRequest.exchange().getRequest();

			UserSessionObject user = getUserSessionDetails(request);
			// APPID,TASKID
			ServiceMeta service = preProcessingFacade.decryptApplyKey(applyKey);

			if (!service.getServiceId().toString().equals(serviceId)) {
				return Mono.error(new SPRuntimeError("Key mismatch", HttpStatus.NOT_ACCEPTABLE, null));
			}

			return preProcessingFacade.getFormDataAndSaveTempTxn(service, user, request).flatMap(response -> {
				// FEResponse re
				return ServerResponse.ok().bodyValue(response);

			}).onErrorResume(Exception.class, ex -> {
				Throwable actual = Exceptions.unwrap(ex);
				if (actual instanceof SPRuntimeError spr) {
					return Mono.error(new SPRuntimeError(spr.getMessage(), spr.getErrorCode(), null));
				}
				ex.printStackTrace();
				return Mono.error(
						new SPRuntimeError("Internal server error [REN - 01]", HttpStatus.INTERNAL_SERVER_ERROR, null));
			});

		} catch (Exception e) {
			e.printStackTrace();
			return Mono.error(new SPRuntimeError("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR, null));
		}
	}

	public Mono<ServerResponse> fetchServiceKey(ServerHttpRequest request, String baseServiceId, String appId,
			String taskId, String serviceId) {
		try {
			UserSessionObject user = getUserSessionDetails(request);

			return preProcessingFacade.fetchServiceKey(Integer.parseInt(baseServiceId), user, request, appId, taskId,
					Integer.parseInt(serviceId));

		} catch (Exception e) {
			e.printStackTrace();
			return Mono.error(new SPRuntimeError("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR, null));
		}
	}

	public Mono<ServerResponse> getWFPInbox(ServerHttpRequest request) {

		try {

			UserSessionObject user = getUserSessionDetails(request);

			return preProcessingFacade.getWFPInbox(request, user)
					.flatMap(response -> ServerResponse.ok().bodyValue(response)).onErrorResume(Exception.class, ex -> {

						Throwable actual = Exceptions.unwrap(ex);
						if (actual instanceof SPRuntimeError spr) {
							return Mono.error(new SPRuntimeError(spr.getMessage(), spr.getErrorCode(), null));
						}

						ex.printStackTrace();
						return Mono.error(new SPRuntimeError("Internal server error [WFP-INBOX-01]",
								HttpStatus.INTERNAL_SERVER_ERROR, null));
					});

		} catch (Exception e) {

			e.printStackTrace();
			return Mono.error(new SPRuntimeError("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR, null));
		}
	}

    public Mono<ServerResponse> getApplicantInbox(ServerHttpRequest request) {

        try {

            UserSessionObject user = getUserSessionDetails(request);

            return DataBufferUtils.join(request.getBody())
                    .map(dataBuffer -> {

                        try {

                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            return new String(bytes, StandardCharsets.UTF_8);

                        } finally {
                            DataBufferUtils.release(dataBuffer);
                        }
                    })
                    .flatMap(requestBody ->
                            preProcessingFacade.getApplicantInbox(requestBody, request, user)
                    )
                    .flatMap(response ->
                            ServerResponse.ok().bodyValue(response)
                    )
                    .onErrorResume(Exception.class, ex -> {

                        Throwable actual = Exceptions.unwrap(ex);

                        if (actual instanceof SPRuntimeError spr) {
                            return Mono.error(new SPRuntimeError(spr.getMessage(), spr.getErrorCode(), null));
                        }

                        ex.printStackTrace();

                        return Mono.error(new SPRuntimeError("Internal server error [APPLICANT-INBOX-01]", HttpStatus.INTERNAL_SERVER_ERROR, null));
                    });

        } catch (Exception e) {

            e.printStackTrace();
            return Mono.error(new SPRuntimeError("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR, null));
        }
    }

	public Mono<ServerResponse> getWFPInboxFilterApplications(ServerHttpRequest request,
			InboxApplReqDTO inboxApplReqDTO) {
		try {
			UserSessionObject user = getUserSessionDetails(request);

			return preProcessingFacade.getWFPInboxFilterApplications(request, user, inboxApplReqDTO)
					.flatMap(response -> ServerResponse.ok().bodyValue(response)).onErrorResume(Exception.class, ex -> {
						Throwable actual = Exceptions.unwrap(ex);
						if (actual instanceof SPRuntimeError spr) {
							return Mono.error(new SPRuntimeError(spr.getMessage(), spr.getErrorCode(), null));
						}
						ex.printStackTrace();
						return Mono.error(new SPRuntimeError("Internal server error [WFP-INBOX-01]",
								HttpStatus.INTERNAL_SERVER_ERROR, null));
					});

		} catch (Exception e) {
			e.printStackTrace();
			return Mono.error(new SPRuntimeError("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR, null));
		}
	}

	public Mono<ServerResponse> getPendingApplications(ServerHttpRequest request) {

		try {

			UserSessionObject user = getUserSessionDetails(request);

			return preProcessingFacade.getInboxApplications(request, user)
					.flatMap(response -> ServerResponse.ok().bodyValue(response)).onErrorResume(Exception.class, ex -> {

						Throwable actual = Exceptions.unwrap(ex);
						if (actual instanceof SPRuntimeError spr) {
							return Mono.error(new SPRuntimeError(spr.getMessage(), spr.getErrorCode(), null));
						}

						ex.printStackTrace();
						return Mono.error(new SPRuntimeError("Internal server error [WFP-INBOX-01]",
								HttpStatus.INTERNAL_SERVER_ERROR, null));
					});

		} catch (Exception e) {

			e.printStackTrace();
			return Mono.error(new SPRuntimeError("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR, null));
		}
	}
}
