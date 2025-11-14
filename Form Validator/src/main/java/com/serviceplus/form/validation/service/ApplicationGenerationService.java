package com.serviceplus.form.validation.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.util.Map;

import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;
import com.serviceplus.form.validation.utility.Utility;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.Services;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.kafka.KafkaProducer;
import com.serviceplus.form.validation.repository.ProcessingTxnRepository;

import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.ApplicationConstants.*;
import static com.serviceplus.form.validation.utility.Utility.isEmpty;

@Service
public class ApplicationGenerationService {
	
	@Autowired
    private ReactiveApiClient reactiveApiClient;

    @Autowired
    private ProcessingTxnRepository txnRepository;
    
    @Autowired
    private KafkaProducer kafka;
    
    @Value("${push.form.submission.data.topic}")
    private String push_form_submission_data_topic;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ApplicationDetailsRepository applicationDetailsRepository;

    @Autowired
    private TransactionalOperator transactionalOperator;

    @Autowired
    private CurrentProcessRepository currentProcessRepository;
    
    public Mono<Map<String, Object>> executeApplicationProcessing(String dataId, Services service, UserSessionObject user, ProcessingTxn txnLog, String appId
                                                                    , String appStatus) {

            if(service.getTaskType().equals(OFFICIAL_TASK_FLAG)){
                return saveTxn(txnLog,dataId,service,user,"",appId,txnLog.getTxnId(),"","",appStatus);
            }
            else{
                return reactiveApiClient.fetchReferenceAbbrviation(service.getServiceId(),user)
                        .flatMap(data -> {
                            try {
                                JSONObject json = new JSONObject(data);
                                String abbr = json.getString("abbr");

                                String referenceNo = abbr.concat("/").concat(String.valueOf(Year.now().getValue())).concat("/").concat(txnLog.getTxnId());

                                return saveTxn(txnLog,dataId,service,user,referenceNo,appId,txnLog.getTxnId(),"","",appStatus);

                            } catch (Exception e) {
                                return Mono.error(new SPRuntimeError(
                                        "Issue while processing the request [SUB - 009]",
                                        HttpStatus.INTERNAL_SERVER_ERROR
                                ));
                            }
                        })
                        .onErrorResume(WebClientResponseException.class, Utility::handleWebClientError);
            }


    }

    private Mono<Map<String, Object>> saveTxn(ProcessingTxn txnLog, String dataId, Services service, UserSessionObject user, String referenceNo, String applicationId,
                                              String currentTxnId, String previousTxnId, String previousTaskId, String appStatus) {
        txnLog.setFormEndTime(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
        txnLog.setNewEntity(false);
        //return transactionalOperator.execute(status ->
        return  txnRepository.save(txnLog)
                                .flatMap(savedLog ->
                                        saveApplicationAndCurrentProcess(savedLog,service,user,referenceNo,applicationId,appStatus)

                )
                .then(Mono.just(Map.of("referenceNo", referenceNo)));
    }

    private Mono<?> saveApplicationAndCurrentProcess(ProcessingTxn savedLog, Services service, UserSessionObject user, String referenceNo, String appId, String appStatus) {

        return applicationDetailsRepository.findById(appId).flatMap(ad ->{
            final Integer status = isEmpty(appStatus) ? FALLBACK_ACTION_NO : Integer.parseInt(appStatus);

            if(service.getTaskType().equals(APPLICATION_SUBMISSION_TASK_FLAG)){
                    ad.setReferenceNo(referenceNo);
                    ad.setStatus("I");
            } else if (service.getTaskType().equals(OFFICIAL_TASK_FLAG)) {
                    ad.setStatus(ACTION_CODE_MAPPING.get(status));
            }

            ad.setNewEntity(false);

            return applicationDetailsRepository.save(ad).flatMap(
                    add -> saveCurrentProcess(add,savedLog,service,user,status)
            );
        });

    }

    private Mono<?> saveCurrentProcess(ApplicationDetails ad, ProcessingTxn savedLog,
                                       Services service, UserSessionObject user, Integer appStatus) {
        return currentProcessRepository.findById(savedLog.getTxnId())
                .flatMap(cp -> {
                    cp.setNewEntity(false);
                    cp.setActionCode(appStatus);
                    cp.setActionTaken("Y");
                    // callForNextCurrentProcess();
                    //FIRST ENTER ENTRY IN FLOW TABLE with completed == false
                    //entry  in application flow ?? I DON'T THINK REQUIRED since empty will fall back to FS
                    return currentProcessRepository.save(cp)
//                            .doOnSuccess( c -> workflowService
//                                                                            .generateNextWorkflow(ad,savedLog,service,user).subscribe()
//                                                                        )
                            ;
                });
    }
}
