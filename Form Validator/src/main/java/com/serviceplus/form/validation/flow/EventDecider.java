package com.serviceplus.form.validation.flow;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.*;
import com.serviceplus.form.validation.entity.ApplicationFlowStatusEntity;
import com.serviceplus.form.validation.entity.ProcessingTxn;
import com.serviceplus.form.validation.repository.ApplicationFlowRouterRepository;
import com.serviceplus.form.validation.service.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.serviceplus.form.validation.utility.Utility.isEmpty;
import static com.serviceplus.form.validation.utility.Utility.populateActionAndLocation;
import static java.util.Objects.isNull;

@Service
public class EventDecider {

    private final EventRouter router;

    private final ApplicationFlowRouterRepository applicationFlowRouterRepository;

    private final ApplicationGenerationService applicationGenerationService;

    private final TransactionGeneration transactionGeneration;

    private final TransactionalDBExecutor transactionalDBExecutor;

    private final ActivityMapService activityMapService;

    private final ReactiveApiClient reactiveApiClient;

    public EventDecider(EventRouter router, ApplicationFlowRouterRepository applicationFlowRouterRepository, ApplicationGenerationService applicationGenerationService, TransactionGeneration transactionGeneration, TransactionalDBExecutor transactionalDBExecutor, ActivityMapService activityMapService, ReactiveApiClient reactiveApiClient) {
        this.router = router;
        this.applicationFlowRouterRepository = applicationFlowRouterRepository;
        this.applicationGenerationService = applicationGenerationService;
        this.transactionGeneration = transactionGeneration;
        this.transactionalDBExecutor = transactionalDBExecutor;
        this.activityMapService = activityMapService;
        this.reactiveApiClient = reactiveApiClient;
    }

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    public Mono<ServerResponse> proceedToNext(String dataId, ServiceMeta service, UserSessionObject user, ProcessingTxn txnLog, String appId
            , String actionCode, String from, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus) {

        if (isEmpty(from)) {
            return Mono.error(new SPRuntimeError("Execution failure [EX -01]", HttpStatus.INTERNAL_SERVER_ERROR,txnLog.getTxnId()));
        }

        applicationFlowLogs.info("Next action for txnId {} applicationId {} ,from {} ,actionCode {} ,isNew {}"
                , txnLog.getTxnId(), appId, from, actionCode, txnLog.isNewEntity());

        if (isNull(flowStatus) || isEmpty(flowStatus.getId())) {
            applicationFlowLogs.info("For applId {} txnId {} flowStatus is empty with activityType {}",appId,txnLog.getTxnId(),from);
            return applicationFlowRouterRepository.findByApplicationIdAndTxnIdAndCompletedAndTaskIdAndServiceIdAndTenantId(
                            appId, txnLog.getTxnId(), 0, service.getTaskId(),service.getServiceId(), user.getTenantId()
                    )
                    .flatMap(flow -> preProcess(dataId, service, user, txnLog, appId
                            , actionCode, from, reactiveRequestObject, flow));
        } else {
            return preProcess(dataId, service, user, txnLog, appId
                    , actionCode, from, reactiveRequestObject, flowStatus);
        }
    }

    private Mono<ServerResponse> preProcess(String dataId, ServiceMeta service, UserSessionObject user, ProcessingTxn txnLog, String appId
            , String actionCode, String from, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus){

        flowStatus.setTxnId(txnLog.getTxnId());
        flowStatus.setCompleted(1);
        flowStatus.setDataId(dataId);
        flowStatus.setLastUpdate(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
        flowStatus.setTenantId(user.getTenantId());
        flowStatus.setApplicationId(appId);
        txnLog.setEndTime(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
        txnLog.setActivityType(flowStatus.getActivityType());

        return transactionalDBExecutor.execute(txnLog.getTxnId(),txnLog)
                .then(
                        process(dataId, service, user, txnLog, appId, actionCode,from, reactiveRequestObject,flowStatus)
                )
                .onErrorResume(Exception.class, ex -> {
                    ex.printStackTrace();
                    Throwable actual = Exceptions.unwrap(ex);
                    applicationFlowLogs.error("Error occurred for txnId {} applicationId {} is {}",txnLog.getTxnId(),appId,ex.getMessage());

                    return   Mono.error(
                            actual instanceof SPRuntimeError spr
                                    ? spr
                                    : new SPRuntimeError("Execution error [EX - 04]",
                                    HttpStatus.INTERNAL_SERVER_ERROR,txnLog.getTxnId())
                    );
                });
    }

    private Mono<ServerResponse> process(String dataId, ServiceMeta service, UserSessionObject user, ProcessingTxn txnLog, String appId
            , String actionCode, String from, ServerRequest reactiveRequestObject, ApplicationFlowStatusEntity flowStatus) {

        return activityMapService
                .getActivityMap(service, user, appId, txnLog.getTxnId())
                .flatMap(activityMap -> {

                    ActivityMapDTO.ActivityData nextActivity = activityMapService.findNextActivity(from, activityMap);

                    if (isNull(nextActivity)) {
                        return Mono.error(new SPRuntimeError("Execution error [EX - 03]", HttpStatus.INTERNAL_SERVER_ERROR,txnLog.getTxnId()));
                    } else {

                        applicationFlowLogs.info("Next activity for txnId {} applicationId {} is {}", txnLog.getTxnId(), appId, nextActivity.toString());

                        if (nextActivity.getActivityType().equals("NA")) {
                            return executeFinalProcessing(dataId, service, user, txnLog, appId,from,flowStatus);
                        } else {
                            return transactionGeneration.createNewTransactionAndFlow(service, appId, nextActivity,
                                    user, reactiveRequestObject.exchange().getRequest(),null
                            ).flatMap(generatedTxn ->
                                            markActivityAsDone(flowStatus)
                                                    .thenReturn(generatedTxn))
                                    .flatMap(generatedTxn ->
                                            router.route(
                                                            nextActivity.getActivityType(),
                                                            appId,
                                                            reactiveRequestObject,
                                                            generatedTxn.getTxnId(),
                                                            service,
                                                            false)
                                                    .onErrorMap(ex -> {

                                                        ex.printStackTrace();
                                                        Throwable actual = Exceptions.unwrap(ex);

                                                        if (actual instanceof SPRuntimeError spr) {
                                                            spr.setTxnId(generatedTxn.getTxnId());
                                                            return spr;
                                                        }

                                                        return new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR, generatedTxn.getTxnId());
                                                    })
                                    );
                        }
                    }
                });
    }

    private Mono<Void> markActivityAsDone(ApplicationFlowStatusEntity flowStatus) {
        return transactionalDBExecutor.execute(flowStatus.getTxnId(),flowStatus);
    }

    private Mono<ServerResponse> executeFinalProcessing(
            String dataId,
            ServiceMeta service,
            UserSessionObject user,
            ProcessingTxn txnLog,
            String appId,
            String from,
            ApplicationFlowStatusEntity flowStatus) {

        String txnId = txnLog.getTxnId();

        applicationFlowLogs.info(
                "TxnId : {} | Starting final processing. dataId={}, appId={}, taskId={}",
                txnId,
                dataId,
                appId,
                service.getTaskId()
        );

        return reactiveApiClient
                .fetchWorkflowAttributes(
                        user, service.getFormId(), dataId, txnId
                )
                .doOnNext(workflowData ->
                        applicationFlowLogs.info("TxnId : {} | Workflow attributes fetched: {}", txnId, workflowData)
                )
                .flatMap(workflowData ->
                        resolveWorkFlowAttributes(workflowData, service, txnId, user)
                )
                .flatMap(actionCode -> {

                    applicationFlowLogs.info("TxnId : {} | Final actionCode resolved as {}", txnId, actionCode);

                    return applicationGenerationService
                            .executeApplicationProcessing(
                                    dataId,
                                    service,
                                    user,
                                    txnLog,
                                    appId,
                                    actionCode,
                                    from
                            )
                            .flatMap(response ->
                                    markActivityAsDone(flowStatus).thenReturn(response)
                            );
                })
                .doOnError(ex ->
                        applicationFlowLogs.error("TxnId : {} | Error during final processing", txnId, ex)
                );
    }

    @SuppressWarnings("unchecked")
    private Mono<String> resolveWorkFlowAttributes(Map<String, Object> workflowData, ServiceMeta service, String txnId, UserSessionObject user) {

        Object actionObject = workflowData.get("action");

        List<Map<String, Object>> selectedTasks = extractList(workflowData.get("task"));

        Map<String, List<Map<String, Object>>> selectedUsers = extractUsers(workflowData.get("user"));

        if (actionObject != null) {

            Map<String, Object> actionMap = null;

            if (actionObject instanceof List<?> actionList && !actionList.isEmpty() && actionList.getFirst() instanceof Map<?, ?> map) {
                actionMap = (Map<String, Object>) map;

            } else if (actionObject instanceof Map<?, ?> map) {
                actionMap = (Map<String, Object>) map;
            }

            if (actionMap != null) {

                Object value = actionMap.get("value");

                if (value != null) {

                    String actionCode = value instanceof Number number ? String.valueOf(number.intValue()) : String.valueOf(value);
                    applicationFlowLogs.info("TxnId : {} | Explicit action selected. actionCode={}", txnId, actionCode);

                    return reactiveApiClient
                            .fetchServiceMetadata(
                                    user,
                                    service.getServiceId(),
                                    txnId
                            )
                            .flatMap(metadataService -> {

                                populateActionAndLocation(metadataService, service);

                                List<WorkFlowDataDTO.WorkFlowAction> availableActions = service.getAvailableActions();

                                if (availableActions == null || availableActions.isEmpty()) {
                                    return Mono.error(new SPRuntimeError("Action configuration not found.", HttpStatus.BAD_REQUEST, txnId));
                                }

                                WorkFlowDataDTO.WorkFlowAction selectedAction =
                                        availableActions.stream()
                                                .filter(action ->
                                                        actionCode.equals(action.getActionKey()))
                                                .findFirst()
                                                .orElse(null);

                                if (selectedAction == null) {
                                    return Mono.error(new SPRuntimeError("Invalid action selected.", HttpStatus.BAD_REQUEST, txnId));
                                }

                                setSelectedWorkflowData(service, selectedAction, selectedTasks, selectedUsers);
                                return Mono.just(actionCode);
                            });
                }
            }
        }

        return reactiveApiClient
                .fetchServiceMetadata(
                        user,
                        service.getServiceId(),
                        txnId
                )
                .flatMap(metadataService -> {

                    populateActionAndLocation(metadataService, service);

                    List<WorkFlowDataDTO.WorkFlowAction> availableActions = service.getAvailableActions();

                    if (availableActions != null && availableActions.size() == 1) {

                        WorkFlowDataDTO.WorkFlowAction action = availableActions.getFirst();
                        applicationFlowLogs.info("TxnId : {} | No action selected. Using configured action {}", txnId, action.getActionKey());
                        setSelectedWorkflowData(service, action, selectedTasks, selectedUsers);

                        return Mono.just(action.getActionKey());
                    }

                    return Mono.error(new SPRuntimeError("No action selected [01].", HttpStatus.BAD_REQUEST, txnId));
                });
    }

    private void setSelectedWorkflowData(ServiceMeta service, WorkFlowDataDTO.WorkFlowAction action, List<Map<String, Object>> selectedTasks, Map<String, List<Map<String, Object>>> selectedUsers) {


        ServiceProcessFlowDTO.Data.ActionAttribute actionAttribute = new ServiceProcessFlowDTO.Data.ActionAttribute();

        actionAttribute.setKey(action.getActionKey());
        actionAttribute.setLabel(action.getActionLabel());
        actionAttribute.setTrackLabel(action.getTrackLabel());
        actionAttribute.setLogicalClosure(action.getLogicalClosure());
        actionAttribute.setCompleteClosure(action.getCompleteClosure());

        ServiceProcessFlowDTO.Data.WorkflowElementData selectedWorkflow = new ServiceProcessFlowDTO.Data.WorkflowElementData();
        selectedWorkflow.setActionAttribute(List.of(actionAttribute));


        if (selectedTasks != null && !selectedTasks.isEmpty()) {

            List<ServiceProcessFlowDTO.Data.TaskNode> selectedTaskNodes =
                    selectedTasks.stream()
                            .map(task -> {

                                ServiceProcessFlowDTO.Data.TaskNode taskNode = new ServiceProcessFlowDTO.Data.TaskNode();

                                Object taskId = task.get("value");
                                taskNode.setTaskId(taskId != null ? String.valueOf(taskId) : null);

                                Object taskName = task.get("label");
                                taskNode.setTaskName(taskName != null ? String.valueOf(taskName) : null);

                                return taskNode;
                            })
                            .toList();

            ServiceProcessFlowDTO.Data.TaskAttribute taskAttribute = new ServiceProcessFlowDTO.Data.TaskAttribute();
            taskAttribute.setTaskNodes(selectedTaskNodes);
            selectedWorkflow.setTaskAttribute(taskAttribute);
        }



        if (selectedUsers != null && !selectedUsers.isEmpty()) {

            List<ServiceProcessFlowDTO.Data.UserNode> selectedUserNodes = new ArrayList<>();

            selectedUsers.forEach((taskId, users) -> {

                if (users == null || users.isEmpty()) {
                    return;
                }

                users.forEach(userData -> {

                    Object holderId = userData.get("value");

                    if (holderId == null) {
                        return;
                    }

                    ServiceProcessFlowDTO.Data.UserNode userNode = new ServiceProcessFlowDTO.Data.UserNode();

                    userNode.setTaskId(taskId);
                    userNode.setHolderId(String.valueOf(holderId));
                    Object holderName = userData.get("label");
                    userNode.setHolderName(holderName != null ? String.valueOf(holderName) : null);
                    selectedUserNodes.add(userNode);
                });
            });

            if (!selectedUserNodes.isEmpty()) {

                ServiceProcessFlowDTO.Data.UserAttribute userAttribute = new ServiceProcessFlowDTO.Data.UserAttribute();

                userAttribute.setSelectionType("MANUAL");
                userAttribute.setUserNodes(selectedUserNodes);
                selectedWorkflow.setUserAttribute(userAttribute);
            }
        }


        service.setSelectedWorkflowElementData(selectedWorkflow);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractList(Object value) {

        if (value instanceof List<?> list) {

            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }

        if (value instanceof Map<?, ?> map) {
            return List.of((Map<String, Object>) map);
        }

        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<Map<String, Object>>> extractUsers(Object value) {

        if (!(value instanceof Map<?, ?> usersMap)) {
            return Collections.emptyMap();
        }

        Map<String, List<Map<String, Object>>> result = new HashMap<>();

        usersMap.forEach((taskId, users) -> {

            if (users instanceof List<?> list) {

                List<Map<String, Object>> userList =
                        list.stream()
                                .filter(Map.class::isInstance)
                                .map(item -> (Map<String, Object>) item)
                                .toList();

                result.put(String.valueOf(taskId), userList);
            }
        });

        return result;
    }
}
