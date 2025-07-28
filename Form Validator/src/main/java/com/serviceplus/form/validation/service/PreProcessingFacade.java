package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.Utility.descryptServiceKeys;
import static com.serviceplus.form.validation.utility.Utility.encryptServiceKeys;
import static com.serviceplus.form.validation.utility.Utility.getClientIpAddr;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.Services;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ProcessingTxnEntity;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;

import reactor.core.publisher.Mono;

@Service
public class PreProcessingFacade {

    @Autowired
    private ReactiveApiClient reactiveApiClient;

    @Autowired
    private ProcessingTxnRepository txnRepository;
    
    @Autowired
    private PostPorcessingFacade postPorcessingFacade;

    public Mono<List<Services>> getServiceList(UserSessionObject user) {
        return reactiveApiClient.fetchServiceList(user)
            .map(services -> {
                services.forEach(service -> {
                	service.setServiceKey(encryptServiceKeys(service));
                });
                return services;
            });
    }

    public Mono<Map<String,Object>> getFormDataAndSaveTxn(Services service, UserSessionObject user, ServerHttpRequest request) {
        String txnId = createUniqueId();

        ProcessingTxnEntity txnEntity = new ProcessingTxnEntity(
            txnId,
            service.getFormId(),
            service.getServiceId(),
            service.getTaskId(),
            LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()),
            null,
            user.getUserID(),
            user.getTenantId(),
            getClientIpAddr(request)
        );

        txnEntity.setNewEntity(true);
        return saveTransactionReactive(txnEntity)
        			.then(reactiveApiClient.fetchFormData(txnId, service.getFormId()));
    }

    private Mono<Void> saveTransactionReactive(ProcessingTxnEntity txnEntity) {
        return txnRepository.save(txnEntity)
            .then();
    }


    public Services decryptApplyKey(String applyKey) {
        return descryptServiceKeys(applyKey);
    }

    
    public Mono<ServerResponse> saveFormData(String txnId, UserSessionObject user, String appData, Services service) {

        return txnRepository.findById(txnId)
            .switchIfEmpty(Mono.error(new SPRuntimeError(
                "Kindly reapply invalid transaction [SUB - 001]", HttpStatus.BAD_REQUEST)))
            .flatMap(txnLog -> {
                
                if (txnLog.getFormEndTime() != null
                        || !service.getServiceId().equals(txnLog.getServiceId())
                        || !service.getFormId().equals(txnLog.getFormId())) {
                    return Mono.error(new SPRuntimeError(
                        "Kindly reapply invalid transaction [SUB - 002]", HttpStatus.BAD_REQUEST));
                }

              
                return reactiveApiClient.saveFormData(txnId, service.getFormId(), appData)
                    .flatMap(apiResponse -> {
                        String body = apiResponse.getBody();
                        ObjectMapper mapper = new ObjectMapper();

                        Map<String, String> responseJson;
                        try {
                            responseJson = mapper.readValue(body, new TypeReference<Map<String, String>>() {});
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                            return Mono.error(new SPRuntimeError(
                                "Issue while processing the request [SUB - 003]", HttpStatus.FAILED_DEPENDENCY));
                        }

                        String message = responseJson.getOrDefault("message", "");
                        String applicationId = responseJson.getOrDefault("applicationId", "");

                        if (apiResponse.getStatusCode().is2xxSuccessful()) {
                            return postPorcessingFacade.executeApplicationProcessing(applicationId, service, user, txnLog);
//                                .onErrorResume(error -> {
//                                    error.printStackTrace();
//                                    return Mono.error(new SPRuntimeError(
//                                        "Issue while processing the request [ERR - 004]", HttpStatus.INTERNAL_SERVER_ERROR));
//                                });
                        }
                        else if(apiResponse.getStatusCode().is4xxClientError()) {
                        	return Mono.error(new SPRuntimeError(
                           		 message + " - issue while processing [SUB - 009]",
                               HttpStatus.BAD_REQUEST));
                        }
                        else {
                            return Mono.error(new SPRuntimeError(
                            		 message + " - issue while processing [SUB - 010]",
                                HttpStatus.UNPROCESSABLE_ENTITY));
                        }
                    });
            })
            .flatMap(resMap ->
                ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(resMap)
            );
    }

}




