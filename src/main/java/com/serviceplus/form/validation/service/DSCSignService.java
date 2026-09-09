package com.serviceplus.form.validation.service;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;

import java.time.LocalDateTime;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.DSCSignRequest;
import com.serviceplus.form.validation.dto.DSCSignResponse;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDocumentLogEntity;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.repository.ApplicationDocumentLogRepository;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class DSCSignService {

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");
	
    private final ReactiveApiClient reactiveApiClient;
    
    private final ApplicationDocumentLogRepository applicationDocumentRepository;

    public DSCSignService(ReactiveApiClient reactiveApiClient, ApplicationDocumentLogRepository applicationDocumentRepository) {
        this.reactiveApiClient = reactiveApiClient;
		this.applicationDocumentRepository = applicationDocumentRepository;
    }

	public Mono<DSCSignResponse> sign(UserSessionObject user, ApplicationFlowStatusEntity flow, ServiceMeta service,
			DSCSignRequest request, String txnId) {

		validate(request);

		return applicationDocumentRepository
				.findByIdAndApplicationIdAndTxnIdAndServiceIdAndTaskIdAndTenantId(request.getDocumentId(), flow.getApplicationId(),
						txnId,flow.getServiceId(), flow.getTaskId(), user.getTenantId())

				.switchIfEmpty(
						Mono.error(new SPRuntimeError("Application document not found", HttpStatus.NOT_FOUND, txnId)))

				.flatMap(documentLog -> {

					String fileId = documentLog.getUploadId();

					if (fileId == null || fileId.isBlank()) {

						return Mono.error(new SPRuntimeError("Document file not found", HttpStatus.NOT_FOUND, txnId));
					}

					request.setFileId(fileId);
					request.setUserId(user.getUserID());
					return reactiveApiClient.signDocument(request, user)

							.flatMap(response -> {

								if (response == null || !response.isSuccess()) {

									return Mono.error(new SPRuntimeError(
											response != null ? response.getMessage() : "DSC signing failed",
											HttpStatus.BAD_REQUEST, txnId));
								}

								/*
								 * File Management has successfully verified and persisted the signed document.
								 *
								 * Now create new document log.
								 */
								ApplicationDocumentLogEntity signedDocumentLog = new ApplicationDocumentLogEntity();

								signedDocumentLog.setNewEntity(true);

								signedDocumentLog.setId(createUniqueId());

								signedDocumentLog.setApplicationId(documentLog.getApplicationId());

								signedDocumentLog.setTxnId(documentLog.getTxnId());

								signedDocumentLog.setProcessId(documentLog.getProcessId());

								signedDocumentLog.setServiceId(documentLog.getServiceId());

								signedDocumentLog.setTaskId(documentLog.getTaskId());

								signedDocumentLog.setReferenceId(documentLog.getReferenceId());

								signedDocumentLog.setDocumentName(documentLog.getDocumentName());

								signedDocumentLog.setSourceType(documentLog.getSourceType());

								signedDocumentLog.setCreatedBy(documentLog.getCreatedBy());

								signedDocumentLog.setCreatedOn(LocalDateTime.now());

								signedDocumentLog.setTenantId(documentLog.getTenantId());
								
								signedDocumentLog.setUploadId(response.getFileId());

								signedDocumentLog.setStatus("S");
								
								response.setDocumentId(signedDocumentLog.getId());

								return applicationDocumentRepository.save(signedDocumentLog).thenReturn(response);
							});
				})

				.doOnError(ex -> applicationFlowLogs.error(
						"DSC signing failed. ApplicationId: {}, " + "ServiceId: {}, txnId: {}", flow.getApplicationId(),
						service.getServiceId(), txnId, ex));
	}

	private void validate(DSCSignRequest request) {

		if (request == null) {
			throw new SPRuntimeError("Invalid request", HttpStatus.BAD_REQUEST, null);
		}

		if (request.getDocumentId() == null || request.getDocumentId().isBlank()) {
			throw new SPRuntimeError("File ID is required", HttpStatus.BAD_REQUEST, null);
		}
		
		if (request.getSignedBase64() == null || request.getSignedBase64().isBlank()) {

			throw new SPRuntimeError("Signed document is required", HttpStatus.BAD_REQUEST, null);
		}

		if (request.getKey() == null || request.getKey().isBlank()) {

			throw new SPRuntimeError("Signing key is required", HttpStatus.BAD_REQUEST, null);
		}

	}
}
