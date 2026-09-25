package com.serviceplus.form.validation.service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTION_CALLBACK;
import static com.serviceplus.form.validation.utility.ApplicationConstants.FALLBACK_ACTION_NO;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;

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

    public Mono<ServerResponse> callback(CallbackRequest request, UserSessionObject user) {

        if (user == null) {
            return Mono.error(new SPRuntimeError(
                    "User session not found", HttpStatus.UNAUTHORIZED, null));
        }

        if (request.getApplicationId() == null
                || request.getApplicationId().isBlank()
                || request.getProcessId() == null
                || request.getProcessId().isBlank()) {

            return Mono.error(new SPRuntimeError(
                    "Application Id and Process Id are required",
                    HttpStatus.BAD_REQUEST,
                    null));
        }

        return currentProcessRepository
                .findByProcessIdAndApplicationIdAndTenantId(
                        request.getProcessId(),
                        request.getApplicationId(),
                        user.getTenantId())
                .switchIfEmpty(Mono.error(new SPRuntimeError(
                        "Process not found for callback",
                        HttpStatus.NOT_FOUND,
                        null)))
                .flatMap(sourceProcess -> {

                    if (!"Y".equals(sourceProcess.getActionTaken())) {
                        return Mono.error(new SPRuntimeError(
                                "Callback is not allowed for this process",
                                HttpStatus.BAD_REQUEST,
                                null));
                    }

                    if (sourceProcess.getUserId() == null
                            || !sourceProcess.getUserId().equals(user.getUserID())) {
                        return Mono.error(new SPRuntimeError(
                                "You are not allowed to callback this application",
                                HttpStatus.FORBIDDEN,
                                null));
                    }

                    return reactiveApiClient
                            .fetchServiceMetadata(user, sourceProcess.getServiceId(), "CALLBACK")
                            .flatMap(serviceJson ->
                                    isInsideDivergentGatewayBranch(
                                            sourceProcess,
                                            serviceJson,
                                            user.getTenantId())
                                            .flatMap(sourceInGatewayBranch ->
                                                    findCallbackTargets(
                                                            sourceProcess,
                                                            user.getTenantId())
                                                            .collectList()
                                                            .flatMap(targetProcesses -> {

                                                                /*
                                                                 * Empty downstream is valid only while a gateway branch is
                                                                 * waiting at convergence. A normal callback must have something
                                                                 * downstream to withdraw.
                                                                 */
                                                                if (targetProcesses.isEmpty()
                                                                        && !sourceInGatewayBranch) {
                                                                    return Mono.error(new SPRuntimeError(
                                                                            "No pending process found for callback",
                                                                            HttpStatus.BAD_REQUEST,
                                                                            null));
                                                                }

                                                                LocalDateTime now = LocalDateTime.now();

                                                                return resolveHistoryAnchor(
                                                                        sourceProcess,
                                                                        targetProcesses,
                                                                        user.getTenantId(),
                                                                        sourceInGatewayBranch)
                                                                        .flatMap(historyAnchorProcess -> {

                                                                            CurrentProcess callbackProcess =
                                                                                    createCallbackProcess(
                                                                                            sourceProcess,
                                                                                            historyAnchorProcess,
                                                                                            now,
                                                                                            sourceInGatewayBranch);

                                                                            Mono<Void> callbackTransaction =
                                                                                    closeTargetsForCallback(
                                                                                            targetProcesses,
                                                                                            request.getApplicationId(),
                                                                                            user.getTenantId(),
                                                                                            now)
                                                                                            .then(currentProcessRepository.save(callbackProcess))
                                                                                            .then()
                                                                                            .as(transactionalOperator::transactional);

                                                                            return callbackTransaction
                                                                                    .then(sendCallbackToTracking(
                                                                                            sourceProcess,
                                                                                            targetProcesses,
                                                                                            callbackProcess,
                                                                                            user))
                                                                                    .then(ServerResponse.ok().bodyValue(
                                                                                            "Application callback completed successfully"));
                                                                        });
                                                            })));
                });
    }

    /*
     * Find the next pending officer task.
     * Gateways are crossed recursively.
     */
    private Flux<CurrentProcess> findCallbackTargets(
            CurrentProcess process,
            String tenantId) {

        return currentProcessRepository
                .findByApplicationIdAndPreviousProcessIdAndTenantId(
                        process.getApplicationId(),
                        process.getProcessId(),
                        tenantId)
                .flatMap(childProcess -> {

                    if (TaskType.GATEWAY.getType()
                            .equals(childProcess.getCurrentTaskType())) {
                        return findCallbackTargets(childProcess, tenantId);
                    }

                    if ("N".equals(childProcess.getActionTaken())) {
                        return Flux.just(childProcess);
                    }

                    return Flux.error(new SPRuntimeError(
                            "Application has already been processed by the next user",
                            HttpStatus.CONFLICT,
                            null));
                });
    }

    /**
     * Walk backwards through the runtime process chain and determine whether
     * the source still belongs to a divergent gateway branch. The walk stops
     * at a convergent gateway so an already-merged task is treated normally.
     */
    private Mono<Boolean> isInsideDivergentGatewayBranch(
            CurrentProcess process,
            ServiceJSONDTO serviceJson,
            String tenantId) {

        if (process == null
                || process.getPreviousProcessId() == null
                || process.getPreviousProcessId().isBlank()) {
            return Mono.just(false);
        }

        return currentProcessRepository
                .findByProcessIdAndApplicationIdAndTenantId(
                        process.getPreviousProcessId(),
                        process.getApplicationId(),
                        tenantId)
                .flatMap(parentProcess -> {

                    ServiceProcessFlowDTO.Data.Nodes parentNode =
                            findNode(serviceJson, parentProcess.getCurrentTask());

                    if (parentNode != null
                            && "gateway".equalsIgnoreCase(parentNode.getType())) {

                        if (isDivergentGateway(parentNode.getBehaviour())) {
                            return Mono.just(true);
                        }

                        if (isConvergentGateway(parentNode.getBehaviour())) {
                            return Mono.just(false);
                        }
                    }

                    return isInsideDivergentGatewayBranch(
                            parentProcess,
                            serviceJson,
                            tenantId);
                })
                .defaultIfEmpty(false);
    }

    /**
     * Resolve the process that should be used only as the history anchor for
     * the new callback process.
     *
     * Normal flow: T1 -> T2, anchor is T2.
     * Divergent flow before split: T1 -> PD -> T2/T3/T5, anchor is the PD
     * execution (the unique immediate child of T1).
     * Branch callback: the callback process keeps the source branch's original
     * previous-process correlation, so the supplied anchor is not used.
     */
    private Mono<CurrentProcess> resolveHistoryAnchor(
            CurrentProcess sourceProcess,
            List<CurrentProcess> targetProcesses,
            String tenantId,
            boolean sourceFromDivergentGateway) {

        if (sourceFromDivergentGateway) {
            return Mono.just(sourceProcess);
        }

        return currentProcessRepository
                .findByApplicationIdAndPreviousProcessIdAndTenantId(
                        sourceProcess.getApplicationId(),
                        sourceProcess.getProcessId(),
                        tenantId
                )
                .collectList()
                .flatMap(immediateChildren -> {

                    if (immediateChildren.size() == 1) {
                        return Mono.just(immediateChildren.get(0));
                    }

                    /*
                     * Fallback for a plain one-step flow when the immediate-child
                     * query and recursive target resolution return the same task.
                     */
                    if (immediateChildren.isEmpty() && targetProcesses.size() == 1) {
                        return Mono.just(targetProcesses.get(0));
                    }

                    return Mono.error(
                            new SPRuntimeError(
                                    "Unable to resolve callback history anchor",
                                    HttpStatus.CONFLICT,
                                    null
                            )
                    );
                });
    }

    /**
     * Withdraw every currently-active downstream consequence of the source
     * process. Multiple targets are valid for divergent gateways:
     * ED -> selected branch, ID -> activated subset, PD -> all activated
     * branches. Runtime rows already represent which branches were activated,
     * therefore we close exactly the pending targets returned by traversal.
     */
    private Mono<Void> closeTargetsForCallback(
            List<CurrentProcess> targetProcesses,
            String applicationId,
            String tenantId,
            LocalDateTime now) {

        if (targetProcesses == null || targetProcesses.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(targetProcesses)
                .concatMap(targetProcess ->
                        currentProcessRepository
                                .closeProcessForCallback(
                                        targetProcess.getProcessId(),
                                        applicationId,
                                        tenantId,
                                        ACTION_CALLBACK
                                )
                                .flatMap(updatedRows -> {
                                    if (updatedRows == null || updatedRows != 1) {
                                        return Mono.error(
                                                new SPRuntimeError(
                                                        "Application has already been processed by the next user",
                                                        HttpStatus.CONFLICT,
                                                        null
                                                )
                                        );
                                    }

                                    targetProcess.setActionTaken("Y");
                                    targetProcess.setActionCode(ACTION_CALLBACK);
                                    targetProcess.setActionOn(now);

                                    return Mono.just(updatedRows);
                                })
                )
                .then();
    }

    /*
     * Send callback through the SAME InboxKafka -> Kafka -> Tracking
     * route used by the normal workflow.
     */
    private Mono<Void> sendCallbackToTracking(
            CurrentProcess sourceProcess,
            List<CurrentProcess> targetProcesses,
            CurrentProcess callbackProcess,
            UserSessionObject user) {

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
                                                 * Exact process that initiated callback.
                                                 * Tracking uses this to mark the correct Sentbox row.
                                                 */
                                                inboxKafka.setCallbackSourceProcessId(
                                                        sourceProcess.getProcessId()
                                                );

                                                /*
                                                 * Send every withdrawn downstream process (Y/25)
                                                 * followed by the new callback source process (N/9).
                                                 * Gateway-waiting callback simply contains the new
                                                 * callback process because there is nothing to withdraw.
                                                 */
                                                List<CurrentProcess> processList = new ArrayList<>();
                                                if (targetProcesses != null) {
                                                    processList.addAll(targetProcesses);
                                                }
                                                processList.add(callbackProcess);
                                                inboxKafka.setProcessList(processList);

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
    
    
    
    
    private ServiceProcessFlowDTO.Data.Nodes findNode(
            ServiceJSONDTO serviceJson,
            String nodeId) {

        if (serviceJson == null
                || serviceJson.getProcessFlowMap() == null
                || serviceJson.getProcessFlowMap().getData() == null
                || nodeId == null) {

            return null;
        }

        return serviceJson.getProcessFlowMap()
                .getData()
                .stream()
                .filter(data -> data.getNode() != null)
                .map(ServiceProcessFlowDTO.Data::getNode)
                .filter(node -> nodeId.equals(node.getId()))
                .findFirst()
                .orElse(null);
    }
    
    
    
    
    private boolean isDivergentGateway(String behaviour) {

        return "ED".equalsIgnoreCase(behaviour)
                || "ID".equalsIgnoreCase(behaviour)
                || "PD".equalsIgnoreCase(behaviour);
    }

    private boolean isConvergentGateway(String behaviour) {

        return "EC".equalsIgnoreCase(behaviour)
                || "IC".equalsIgnoreCase(behaviour)
                || "PC".equalsIgnoreCase(behaviour);
    }
    
    
    
    
    
    

    private CurrentProcess createCallbackProcess(
            CurrentProcess sourceProcess,
            CurrentProcess historyAnchorProcess,
            LocalDateTime now,
            boolean sourceFromDivergentGateway) {

        CurrentProcess callbackProcess = new CurrentProcess();

        callbackProcess.setProcessId(createUniqueId());

        callbackProcess.setServiceId(sourceProcess.getServiceId());
        callbackProcess.setBaseServiceId(sourceProcess.getBaseServiceId());
        callbackProcess.setApplicationId(sourceProcess.getApplicationId());
        callbackProcess.setTenantId(sourceProcess.getTenantId());

        /*
         * Callback ke baad wahi source task
         * dobara pending banega.
         */
        callbackProcess.setCurrentTask(
                sourceProcess.getCurrentTask()
        );

        callbackProcess.setCurrentTaskType(
                sourceProcess.getCurrentTaskType()
        );

        callbackProcess.setCurrentTaskName(
                sourceProcess.getCurrentTaskName()
        );

        if (sourceFromDivergentGateway) {

            /*
             * Gateway branch callback.
             *
             * Example:
             *
             * PD process = PG100
             *
             * Old T2:
             * previousProcessId = PG100
             * previousTask      = g_1
             *
             * New T2 ko bhi SAME gateway execution
             * ke andar rehna hai.
             */
            callbackProcess.setPreviousProcessId(
                    sourceProcess.getPreviousProcessId()
            );

            callbackProcess.setPreviousTask(
                    sourceProcess.getPreviousTask()
            );

            callbackProcess.setPreviousTaskName(
                    sourceProcess.getPreviousTaskName()
            );

        } else {

            /*
             * Normal callback:
             *
             * P100 -> P200 -> P300
             */
            callbackProcess.setPreviousProcessId(
                    historyAnchorProcess.getProcessId()
            );

            callbackProcess.setPreviousTask(
                    historyAnchorProcess.getCurrentTask()
            );

            callbackProcess.setPreviousTaskName(
                    historyAnchorProcess.getCurrentTaskName()
            );
        }

        callbackProcess.setActionTaken("N");
        callbackProcess.setInitiatedOn(now);
        callbackProcess.setActionCode(FALLBACK_ACTION_NO);
        callbackProcess.setNewEntity(true);

        return callbackProcess;
    }
}
