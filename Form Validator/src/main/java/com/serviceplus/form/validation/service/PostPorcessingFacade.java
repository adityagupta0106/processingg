package com.serviceplus.form.validation.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.Services;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ProcessingTxnEntity;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;

import reactor.core.publisher.Mono;

@Service
public class PostPorcessingFacade {
	
	@Autowired
    private ReactiveApiClient reactiveApiClient;

    @Autowired
    private ProcessingTxnRepository txnRepository;
    
    public Mono<Map<String, String>> executeApplicationProcessing(String applicationId, Services service, UserSessionObject user, ProcessingTxnEntity txnLog) {

        return reactiveApiClient.fetchReferenceAbbrviation(service.getServiceId(),user)
            .flatMap(data -> {
                try {
                    JSONObject json = new JSONObject(data);
                    String abbr = json.getString("abbr");

                    String referenceNo = abbr + "/" + Year.now().getValue() + "/" + txnLog.getTxnId();

                    txnLog.setFormEndTime(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
                    txnLog.setActualApplicationId(applicationId);
                    txnLog.setApplicationReferenceNo(referenceNo);

                    return txnRepository.save(txnLog)
                            .map(savedTxn -> {
                                Map<String, String> responseMap = new HashMap<>();
                                responseMap.put("referenceNo", referenceNo);
                                return responseMap;
                            });

                } catch (Exception e) {
                    return Mono.error(new SPRuntimeError(
                        "Issue while processing the request [SUB - 008]",
                        HttpStatus.INTERNAL_SERVER_ERROR
                    ));
                }
            });
    }
    
    //KAFKA ROUTING,DOCUMENTS SERVICE?????
//    CURRENT PROCESS HERE??

}
