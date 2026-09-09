package com.serviceplus.form.validation.auaVerification.controller;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.auaVerification.dto.AuaRequest;
import com.serviceplus.form.validation.auaVerification.enums.AuaOperationType;
import com.serviceplus.form.validation.auaVerification.service.ApplicationCryptoService;
import com.serviceplus.form.validation.auaVerification.service.AuaService;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static com.serviceplus.form.validation.utility.Utility.*;


@Component
public class AuaController {

    private static final Logger log = LoggerFactory.getLogger("applicationFlowLogger");

    @Value("${application.crypto.public-key}")
    private Resource publicKeyResource;


    private final AuaService auaService;

    public AuaController(AuaService auaService) {
        this.auaService = auaService;
    }


    public Mono<ServerResponse> request(ServerRequest request) {

        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());

        log.info("AUA execution request received");

        return request.bodyToMono(String.class)
                .flatMap(auaRequestStr -> {

                    AuaRequest auaRequest = (AuaRequest) stringToEntity(auaRequestStr,AuaRequest.class);

                    if (auaRequest.getOperationType() == null || auaRequest.getOperationType().isBlank()) {

                        log.error("AUA operation type is missing. serviceId={}, txnId={}",
                                auaRequest.getServiceId(),
                                auaRequest.getTxnId()
                        );

                        return Mono.error(new IllegalArgumentException("AUA operationType is required"));
                    }

                    if (!isValidOperation(auaRequest.getOperationType())) {

                        log.error("Invalid AUA operation type. operationType={}, serviceId={}, txnId={}",
                                auaRequest.getOperationType(),
                                auaRequest.getServiceId(),
                                auaRequest.getTxnId()
                        );

                        return Mono.error(new IllegalArgumentException("Invalid AUA operationType: " + auaRequest.getOperationType()));
                    }

                    Optional<String> serviceKey = request.queryParam("serviceKey");

                    if(serviceKey.isEmpty() || isEmpty(auaRequest.getTxnId())){
                        return Mono.error(new SPRuntimeError("Mandatory parameters is required", HttpStatus.BAD_REQUEST,auaRequest.getTxnId()));
                    }

                    ServiceMeta serviceMeta = null;

                    try{
                        serviceMeta = decryptServiceKeys(serviceKey.get());
                        auaRequest.setServiceId(serviceMeta.getServiceId());
                        auaRequest.setTaskId(serviceMeta.getTaskId());
                    }
                    catch (Exception e){
                        e.printStackTrace();
                        return Mono.error(new SPRuntimeError("Invalid service key", HttpStatus.BAD_REQUEST,auaRequest.getTxnId()));
                    }

                    return Mono.just(auaRequest);
                })
                .doOnNext(auaRequest -> log.info("AUA request parsed. serviceId={}, taskId={}, txnId={}, operationType={}",
                        auaRequest.getServiceId(),
                        auaRequest.getTaskId(),
                        auaRequest.getTxnId(),
                        auaRequest.getOperationType())
                )
                .flatMap(auaRequest ->
                        auaService.execute(auaRequest, user)
                )
                .flatMap(response ->
                        ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(response)
                )
                .doOnError(error ->
                        log.error("AUA controller execution failed. error={}", error.getMessage(), error)
                );
    }

    public Mono<ServerResponse> validate(ServerRequest request) {

        UserSessionObject user = getUserSessionDetails(request.exchange().getRequest());

        log.info("AUA validation request received");

        return request.bodyToMono(String.class)

                .flatMap(auaRequestStr -> {

                    AuaRequest auaRequest = (AuaRequest) stringToEntity(auaRequestStr, AuaRequest.class);

                    if (auaRequest == null) {
                        log.error("AUA validation request is empty");

                        return Mono.error(new SPRuntimeError("Invalid AUA validation request", HttpStatus.BAD_REQUEST, null));
                    }

                    log.info("AUA validation request parsed. txnId={}, attributeId={}", auaRequest.getTxnId(), auaRequest.getAttributeId());


                    if (isEmpty(auaRequest.getTxnId())) {
                        return Mono.error(new SPRuntimeError("Transaction ID is required", HttpStatus.BAD_REQUEST, null));
                    }

                    if (isEmpty(auaRequest.getAttributeId())) {
                        return Mono.error(new SPRuntimeError("Attribute ID is required", HttpStatus.BAD_REQUEST, auaRequest.getTxnId()));
                    }

                    Optional<String> serviceKey = request.queryParam("serviceKey");

                    if (serviceKey.isEmpty()) {
                        return Mono.error(new SPRuntimeError("Service key is required", HttpStatus.BAD_REQUEST, auaRequest.getTxnId()));
                    }


                    try {

                        ServiceMeta serviceMeta = decryptServiceKeys(serviceKey.get());
                        auaRequest.setServiceId(serviceMeta.getServiceId());
                        auaRequest.setTaskId(serviceMeta.getTaskId());

                    } catch (Exception e) {

                        log.error("Invalid service key during AUA validation. txnId={}", auaRequest.getTxnId(), e);
                        return Mono.error(new SPRuntimeError("Invalid service key", HttpStatus.BAD_REQUEST, auaRequest.getTxnId()));
                    }

                    return Mono.just(auaRequest);
                })

                .doOnNext(auaRequest -> log.info("AUA validation prepared. serviceId={}, taskId={}, txnId={}, attributeId={}, operationType={}", auaRequest.getServiceId(), auaRequest.getTaskId(), auaRequest.getTxnId(), auaRequest.getAttributeId(), auaRequest.getOperationType()))

                .flatMap(auaRequest -> auaService.execute(auaRequest, user))

                .flatMap(response -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(response))

                .doOnError(error -> log.error("AUA validation failed. error={}", error.getMessage(), error));
    }

    public Mono<ServerResponse> getPublicKey(ServerRequest request) {

        try {
            return ServerResponse.ok().bodyValue(new String(publicKeyResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        } catch (IOException e) {
            throw new IllegalStateException("Unable to load application public key", e);
        }
    }

    private boolean isValidOperation(String operationType) {

        try {

            AuaOperationType.valueOf(operationType.trim().toUpperCase());
            return true;

        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}