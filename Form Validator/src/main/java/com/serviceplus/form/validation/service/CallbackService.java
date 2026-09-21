package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTION_CALLBACK;
import static com.serviceplus.form.validation.utility.ApplicationConstants.FALLBACK_ACTION_NO;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.CallbackRequest;
import com.serviceplus.form.validation.dto.InboxKafka;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.TaskAvailableOfficeLocation;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.dto.WorkflowAssignmentDTO;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.enums.TaskType;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class CallbackService {

    private final CurrentProcessRepository currentProcessRepository;
    private final TransactionalOperator transactionalOperator;
    private final ApplicationDetailsRepository applicationDetailsRepository;
    private final ReactiveApiClient reactiveApiClient;
    private final ApplicationGenerationService applicationGenerationService;

    public CallbackService(CurrentProcessRepository currentProcessRepository,TransactionalOperator transactionalOperator,ApplicationDetailsRepository applicationDetailsRepository,ReactiveApiClient reactiveApiClient,ApplicationGenerationService applicationGenerationService) {

        this.currentProcessRepository = currentProcessRepository;
        this.transactionalOperator = transactionalOperator;
        this.applicationDetailsRepository = applicationDetailsRepository;
        this.reactiveApiClient = reactiveApiClient;
        this.applicationGenerationService = applicationGenerationService;
    }

    public Mono<ServerResponse> callback(CallbackRequest request,UserSessionObject user) {

        if (user == null) {
            return Mono.error(new SPRuntimeError("User session not found",HttpStatus.UNAUTHORIZED,null));
        }

        if (request.getApplicationId() == null || request.getApplicationId().isBlank() || request.getProcessId() == null
                || request.getProcessId().isBlank()) {

            return Mono.error(new SPRuntimeError("Application Id and Process Id are required",HttpStatus.BAD_REQUEST,null));
        }

        return currentProcessRepository .findByProcessIdAndApplicationIdAndTenantId(
                        request.getProcessId(),
                        request.getApplicationId(),
                        user.getTenantId()
                )
                .switchIfEmpty(Mono.error(new SPRuntimeError("Process not found for callback",HttpStatus.NOT_FOUND, null )))
                
                .flatMap(sourceProcess -> {

                    if (!"Y".equals(sourceProcess.getActionTaken())) {
                        return Mono.error(new SPRuntimeError("Callback is not allowed for this process",HttpStatus.BAD_REQUEST,null));
                    }

                    if (sourceProcess.getUserId() == null || !sourceProcess.getUserId().equals(user.getUserID())) {

                        return Mono.error(new SPRuntimeError("You are not allowed to callback this application",HttpStatus.FORBIDDEN,null));
                    }

                    return findCallbackTargets(sourceProcess,user.getTenantId())
                    .collectList()
                    .flatMap(targetProcesses -> {

                        if (targetProcesses.isEmpty()) {
                            return Mono.error(new SPRuntimeError("No pending process found for callback",HttpStatus.BAD_REQUEST,null));
                        }

                        if (targetProcesses.size() > 1) {
                            return Mono.error(new SPRuntimeError("Callback for parallel workflow is not supported yet",HttpStatus.BAD_REQUEST,null));
                        }

                        CurrentProcess targetProcess = targetProcesses.get(0);
                        LocalDateTime now = LocalDateTime.now();

                        CurrentProcess callbackProcess =createCallbackProcess( sourceProcess,targetProcess,now);

                        Mono<Void> callbackTransaction =currentProcessRepository.closeProcessForCallback(
                                                targetProcess.getProcessId(),
                                                request.getApplicationId(),
                                                user.getTenantId(),
                                                ACTION_CALLBACK)
                        		
                                        .flatMap(updatedRows -> {

                                            if (updatedRows == null || updatedRows != 1) {
                                                return Mono.error(new SPRuntimeError("Application has already been processed by the next user",HttpStatus.CONFLICT,null));
                                            }

                                            /*
                                             * closeProcessForCallback() updates P200 in DB.
                                             * Update this in-memory P200 too because the same
                                             * object is sent to Tracking through Kafka.
                                             */
                                            targetProcess.setActionTaken("Y");
                                            targetProcess.setActionCode(ACTION_CALLBACK);
                                            targetProcess.setActionOn(now);

                                            return currentProcessRepository.save(callbackProcess).then();  
                                          })
                                        .as(transactionalOperator::transactional);

                        return callbackTransaction.then(sendCallbackToTracking(targetProcess,callbackProcess,user))
                        		
                                .then(ServerResponse.ok().bodyValue("Application callback completed successfully"));
                    });
                });
    }

    /*
     * Find the next pending officer task.
     * Gateways are crossed recursively.
     */
    private Flux<CurrentProcess> findCallbackTargets(CurrentProcess process,String tenantId) {

        return currentProcessRepository.findByApplicationIdAndPreviousProcessIdAndTenantId(
                        process.getApplicationId(),
                        process.getProcessId(),
                        tenantId
                )
                .switchIfEmpty(Flux.error(new SPRuntimeError("No next process found",HttpStatus.BAD_REQUEST,null))
                )
                .flatMap(childProcess -> {

                    if (TaskType.GATEWAY.getType().equals(childProcess.getCurrentTaskType())) {

                        return findCallbackTargets(childProcess,tenantId);
                    }

                    if ("N".equals(childProcess.getActionTaken())) {
                        return Flux.just(childProcess);
                    }

                    return Flux.error(new SPRuntimeError("Application has already been processed by the next user",HttpStatus.BAD_REQUEST,null));
                });
    }

    /*
     * Send callback through the SAME InboxKafka -> Kafka -> Tracking
     * route used by the normal workflow.
     */
    private Mono<Void> sendCallbackToTracking(CurrentProcess targetProcess,CurrentProcess callbackProcess, UserSessionObject user) {

        if (user.getLocationId() == null) {
            return Mono.error(new SPRuntimeError("User location not found for callback",HttpStatus.BAD_REQUEST,null));
        }

        return applicationDetailsRepository.findByApplicationIdAndTenantId(callbackProcess.getApplicationId(),user.getTenantId())
        		
                .switchIfEmpty(Mono.error(new SPRuntimeError("Application details not found",HttpStatus.NOT_FOUND,null))
                )
                .flatMap(application ->reactiveApiClient.fetchServiceKey(
                                        callbackProcess.getBaseServiceId(),
                                        user,
                                        callbackProcess.getApplicationId(),
                                        callbackProcess.getCurrentTask(),
                                        callbackProcess.getServiceId()
                                )
                                .flatMap(service -> {

                                    /*
                                     * formId is transient in CurrentProcess, so populate it
                                     * before P300 is sent to Tracking.
                                     */
                                    callbackProcess.setFormId(service.getFormId());
                                    callbackProcess.setIsPriority(application.getIsPriority());

                                    return reactiveApiClient.fetchWorkflowAssignments(
                                                    callbackProcess.getServiceId(),
                                                    callbackProcess.getCurrentTask(),
                                                    String.valueOf(user.getLocationId()),
                                                    user,
                                                    "CALLBACK"
                                            )
                                            .flatMap(assignments -> {

                                                List<String> holderIds = assignments.stream()
                                                                .filter(Objects::nonNull)
                                                                .filter(a ->
                                                                        a.getUserId() != null
                                                                        && user.getUserID() != null
                                                                        && user.getUserID().equals(
                                                                                a.getUserId().longValue()
                                                                        )
                                                                )
                                                                .map(WorkflowAssignmentDTO::getHolderId)
                                                                .filter(Objects::nonNull)
                                                                .map(String::trim)
                                                                .filter(holderId -> !holderId.isEmpty())
                                                                .distinct()
                                                                .toList();

                                                if (holderIds.isEmpty()) {
                                                    return Mono.error(new SPRuntimeError("Callback user holder not found",HttpStatus.BAD_REQUEST,null));
                                                }

                                                ServiceMeta.AvailableApplyLocations location =new ServiceMeta.AvailableApplyLocations();

                                                location.setOrgUnitCode(user.getLocationId().longValue());
                                                location.setOrgUnitName(user.getLocationName());
                                                location.setHolderIds(holderIds);

                                                TaskAvailableOfficeLocation office =new TaskAvailableOfficeLocation();

                                                office.setTaskId(callbackProcess.getCurrentTask());
                                                office.setAllowedOffices(List.of(location));

                                                InboxKafka inboxKafka =new InboxKafka();
                                                
                                                

                                                /*
                                                 * P200 -> Y / ACTION_CALLBACK
                                                 * P300 -> N
                                                 */
                                                inboxKafka.setProcessList(List.of(targetProcess,callbackProcess));

                                                inboxKafka.setOfficeDetails(List.of(office));

                                                inboxKafka.setServiceName(service.getServiceName());
                                                inboxKafka.setAppliedBy(application.getBeneficiaryId());
                                                inboxKafka.setBeneficiaryName(application.getBeneficiaryName());
                                                inboxKafka.setApplyDate(application.getApplyDate());
                                                inboxKafka.setLoggedInUserId(user.getUserID());
                                                inboxKafka.setLoggedInUserLocation(user.getLocationId());
                                                inboxKafka.setLocationId(user.getLocationId().longValue());
                                                inboxKafka.setLocationName(user.getLocationName());

                                                /*
                                                 * Reuse the existing normal workflow sender.
                                                 * sendToInboxService() sets applicationRefNo and
                                                 * publishes the InboxKafka payload to the existing topic.
                                                 */
                                                return applicationGenerationService.sendToInboxService(inboxKafka,application,service)
                                                        .then();
                                                });
                                })
                );
    }

    /*
     * Create P300 - new pending process for the source task.
     */
    private CurrentProcess createCallbackProcess(CurrentProcess sourceProcess,CurrentProcess targetProcess,
            LocalDateTime now) {

        CurrentProcess callbackProcess =new CurrentProcess();

        callbackProcess.setProcessId(createUniqueId());

        callbackProcess.setServiceId(sourceProcess.getServiceId());

        callbackProcess.setBaseServiceId(sourceProcess.getBaseServiceId());

        callbackProcess.setApplicationId(sourceProcess.getApplicationId());

        callbackProcess.setTenantId(sourceProcess.getTenantId());

        callbackProcess.setCurrentTask(sourceProcess.getCurrentTask());

        callbackProcess.setCurrentTaskType(sourceProcess.getCurrentTaskType());

        callbackProcess.setCurrentTaskName(sourceProcess.getCurrentTaskName());

        /*
         * Preserve process history:
         * P100 -> P200 -> P300
         */
        callbackProcess.setPreviousProcessId(targetProcess.getProcessId());

        callbackProcess.setPreviousTask(targetProcess.getCurrentTask());

        callbackProcess.setPreviousTaskName(targetProcess.getCurrentTaskName());

        callbackProcess.setActionTaken("N");
        callbackProcess.setInitiatedOn(now);
        callbackProcess.setActionCode(FALLBACK_ACTION_NO);

        callbackProcess.setNewEntity(true);

        return callbackProcess;
    }
}
