package com.serviceplus.form.validation.service;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.OfficeDetailsDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.TaskAvailableOfficeLocation;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;

import static com.serviceplus.form.validation.utility.ApplicationConstants.TYPE_GATEWAY;

@Service
public class TaskAssignmentService {

    @Autowired
    private ReactiveApiClient apiClient;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

    public Mono<TaskAvailableOfficeLocation> nextAllowedOfficeLocation(List<ServiceProcessFlowDTO.Data> wf,
                                                                        ServiceProcessFlowDTO.Data.Nodes next, String txnId, Integer serviceId, UserSessionObject user,
                                                                        Map<String, Map<String, List<String>>> taskLocationHolderMap,ServiceMeta serviceMeta) {

        applicationFlowLogs.info("Calculating nextAllowedOfficeLocation for txnId={}, serviceId={}, taskId={}", txnId, serviceId, next.getId());

        TaskAvailableOfficeLocation location = new TaskAvailableOfficeLocation();
        location.setTaskId(next.getId());
        List<ServiceMeta.AvailableApplyLocations> allowedOffices = wf.stream()
                .filter(d -> d.getNode() != null)
                .filter(d -> next.getId().equals(d.getNode().getId()))
                .map(ServiceProcessFlowDTO.Data::getAllowedOffices)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(Collections.emptyList())
                .stream()
                .map(this::mapAvailableLocation)
                .collect(Collectors.toList());

        applicationFlowLogs.info("Allowed offices found for txnId={}, taskId={} : {}", txnId, next.getId(), allowedOffices);

        List<ServiceProcessFlowDTO.Data.UserNode> userNodes = serviceMeta.getSelectedWorkflowElementData() == null ||
                                                                    serviceMeta.getSelectedWorkflowElementData().getUserAttribute() == null
                                                                    ? List.of()
                                                                    : serviceMeta.getSelectedWorkflowElementData().getUserAttribute().getUserNodes();


        if (!userNodes.isEmpty() && !next.getType().equals(TYPE_GATEWAY)) {

            applicationFlowLogs.info("Using holderIds from workflow metadata for txnId={}, taskId={}", txnId, next.getId());

            Map<String, List<String>> locationHolderMap =
                    taskLocationHolderMap.computeIfAbsent(next.getId(), k -> new HashMap<>());

            userNodes.stream()
                    .filter(u -> u.getTaskId().equals(next.getId()))
                    .forEach(u ->
                            locationHolderMap
                                    .computeIfAbsent(String.valueOf(u.getLocationId()), k -> new ArrayList<>())
                                    .add(u.getHolderId()));

            locationHolderMap.replaceAll((k, v) ->
                    v.stream().filter(Objects::nonNull).distinct().toList());

            allowedOffices.forEach(office ->
                    office.setHolderIds(
                            locationHolderMap.getOrDefault(
                                    String.valueOf(office.getOrgUnitCode()),
                                    List.of())));

            location.setAllowedOffices(allowedOffices);

            return Mono.just(location);
        }

        location.setAllowedOffices(allowedOffices);

        return Flux.fromIterable(allowedOffices)

                .doOnNext(office -> applicationFlowLogs.info("Fetching workflow assignments for txnId={}, taskId={}, locationId={}", txnId, next.getId(), office.getOrgUnitCode()))

                .flatMap(office -> apiClient.fetchWorkflowAssignments(serviceId, next.getId(), office.getOrgUnitCode().toString(), user, txnId))

                .doOnNext(assignments -> applicationFlowLogs.info(
                        "Workflow assignments received for txnId={}, taskId={} : {}", txnId, next.getId(), assignments))

                .flatMapIterable(assignments -> assignments)
                .doOnNext(assignment -> applicationFlowLogs.info("Assignment -> locationId={}, holderId={}", assignment.getLocationId(), assignment.getHolderId()))

                .collectList()
                .map(assignments -> {

                    applicationFlowLogs.info("Total assignments fetched for txnId={}, taskId={} : {}", txnId, next.getId(), assignments.size());

                    Map<String, List<String>> locationHolderMap = taskLocationHolderMap.computeIfAbsent(next.getId(), k -> new HashMap<>());

                    assignments.stream().filter(a -> a.getLocationId() != null).forEach(a -> {

                        applicationFlowLogs.info("Adding holderId={} for locationId={} taskId={}", a.getHolderId(), a.getLocationId(), next.getId());
                        locationHolderMap.computeIfAbsent(a.getLocationId(), k -> new ArrayList<>()).add(a.getHolderId());
                    });

                    applicationFlowLogs.info("Location holder map before duplicate removal for taskId={} : {}",
                            next.getId(), locationHolderMap);

                    locationHolderMap.replaceAll(
                            (locationId, holders) -> holders.stream().filter(Objects::nonNull).distinct().toList());

                    applicationFlowLogs.info("Location holder map after duplicate removal for taskId={} : {}",
                            next.getId(), locationHolderMap);

                    location.getAllowedOffices().forEach(office -> {

                        String officeId = office.getOrgUnitCode().toString();

                        List<String> holderIds = locationHolderMap.getOrDefault(officeId, List.of());

                        office.setHolderIds(holderIds);

                        applicationFlowLogs.info("Mapped office locationId={} with holderIds={} for taskId={}",
                                officeId, holderIds, next.getId());
                    });

                    applicationFlowLogs.info("Final taskLocationHolderMap for txnId={}, taskId={} : {}", txnId,
                            next.getId(), taskLocationHolderMap);

                    applicationFlowLogs.info("Final TaskAvailableOfficeLocation for txnId={}, taskId={} : {}", txnId,
                            next.getId(), location);

                    return location;
                })

                .defaultIfEmpty(location)

                .doOnSuccess(result -> applicationFlowLogs.info(
                        "Completed nextAllowedOfficeLocation for txnId={}, taskId={}, result={}", txnId, next.getId(),
                        result))

                .onErrorResume(ex -> {

                    applicationFlowLogs.error("Error fetching workflow assignments for txnId={}, taskId={}", txnId,
                            next.getId(), ex);

                    return Mono.just(location);
                });
    }

    public ServiceMeta.AvailableApplyLocations mapAvailableLocation(OfficeDetailsDTO.OfficeUnitData office) {

        ServiceMeta.AvailableApplyLocations location = new ServiceMeta.AvailableApplyLocations();

        location.setOrgUnitCode(office.getOrgUnitCode() != null ? office.getOrgUnitCode().longValue() : null);
        location.setOrgUnitName(office.getOrgUnitName());
        location.setHolderIds(new ArrayList<>());

        return location;
    }

    public Mono<Void> executeAfterTaskMvel(UserSessionObject user,ServiceMeta service, ApplicationDetails applicationDetails, ProcessingTxn txn, String appData,
                                            CurrentProcess currentActionProcess, String taskId, Map<String, Map<String,List<String>>> taskLocationUserHolderMap, Map<String, Date> timerDueDate
    ) {

        return apiClient.fetchMvelDetails(user,service.getServiceId(),txn.getTxnId())
                .flatMapMany(Flux::fromIterable)
                .filter(m -> "AT".equalsIgnoreCase(m.getValue())).filter(m -> taskId.equals(m.getNodeId()))
                .flatMap(m ->
                             apiClient.executeMvel(m.getMvelId(),
                                             txn.getTxnId(),
                                             "",
                                             "AT",
                                             currentActionProcess.getApplicationId(),
                                            service.getServiceId(),
                                             null,
                                             appData,
                                             null,
                                             null,
                                             taskLocationUserHolderMap,
                                             null)
                        .flatMap(response -> {
                            if (!response.isSuccess()) {
                                return Mono.error(new SPRuntimeError("After Task MVEL failed", HttpStatus.BAD_GATEWAY,
                                        txn.getTxnId()));
                            }
                            if (response.getTaskLocationUserHolderMap() != null) {
                                taskLocationUserHolderMap.clear();
                                taskLocationUserHolderMap.putAll(response.getTaskLocationUserHolderMap());
                            }
                            if (response.getTimerDueDate()!=null && !response.getTimerDueDate().isEmpty()) {
                            	timerDueDate.clear();
                            	timerDueDate.putAll(response.getTimerDueDate());
                            }
                            return Mono.empty();
                        }))
                .then();
    }

    public void refreshTaskAvailableOfficeLocation(TaskAvailableOfficeLocation officeLocation,
                                                    Map<String, Map<String, List<String>>> taskLocationUserHolderMap) {

        applicationFlowLogs.info("Refreshing office locations for taskId={}, taskLocationUserHolderMap={}",
                officeLocation.getTaskId(), taskLocationUserHolderMap);

        Map<String, List<String>> locationHolderMap = taskLocationUserHolderMap.getOrDefault(officeLocation.getTaskId(),
                Map.of());

        applicationFlowLogs.info("Location holder map for taskId {} : {}", officeLocation.getTaskId(),
                locationHolderMap);

        List<ServiceMeta.AvailableApplyLocations> filteredOffices = officeLocation.getAllowedOffices().stream()
                .filter(office -> {

                    String locationId = String.valueOf(office.getOrgUnitCode());

                    List<String> holderIds = locationHolderMap.get(locationId);

                    applicationFlowLogs.info("Evaluating taskId={}, locationId={}, holderIds={}",
                            officeLocation.getTaskId(), locationId, holderIds);

                    if (holderIds == null || holderIds.isEmpty()) {

                        applicationFlowLogs.info("Removing locationId={} for taskId={} because no holders found",
                                locationId, officeLocation.getTaskId());

                        return false;
                    }

                    office.setHolderIds(holderIds);

                    applicationFlowLogs.info("Retaining locationId={} for taskId={} with holderIds={}", locationId,
                            officeLocation.getTaskId(), holderIds);

                    return true;
                }).toList();

        applicationFlowLogs.info("Filtered offices for taskId={} before={}, after={}", officeLocation.getTaskId(),
                officeLocation.getAllowedOffices().size(), filteredOffices.size());

        officeLocation.setAllowedOffices(new ArrayList<>(filteredOffices));

        applicationFlowLogs.info("Final office locations for taskId={} : {}", officeLocation.getTaskId(),
                officeLocation.getAllowedOffices());
    }
}
