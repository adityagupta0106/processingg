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
import com.serviceplus.form.validation.dto.ApplicationRouting;
import com.serviceplus.form.validation.dto.OfficeDetailsDTO;
import com.serviceplus.form.validation.dto.RoutingSource;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.TaskAvailableOfficeLocation;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.dto.ApplicationRouting.RoutingAttribute;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;

import static com.serviceplus.form.validation.utility.ApplicationConstants.TYPE_GATEWAY;
import static java.util.Objects.isNull;

import java.time.LocalDateTime;

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



        location.setAllowedOffices(allowedOffices);

        return Flux.fromIterable(allowedOffices)

                .doOnNext(office -> applicationFlowLogs.info(
                        "Fetching workflow assignments for txnId={}, taskId={}, locationId={}", txnId, next.getId(),
                        office.getOrgUnitCode()))

                .flatMap(office -> apiClient.fetchWorkflowAssignments(serviceId, next.getId(),
                        office.getOrgUnitCode().toString(), user, txnId))

                .doOnNext(assignments -> applicationFlowLogs.info(
                        "Workflow assignments received for txnId={}, taskId={} : {}", txnId, next.getId(), assignments))

                .flatMapIterable(assignments -> assignments)

                .doOnNext(assignment -> applicationFlowLogs.info("Assignment -> locationId={}, holderId={}",
                        assignment.getLocationId(), assignment.getHolderId()))

                .collectList()

                .map(assignments -> {

                    applicationFlowLogs.info("Total assignments fetched for txnId={}, taskId={} : {}", txnId,
                            next.getId(), assignments.size());

                    Map<String, List<String>> locationHolderMap = taskLocationHolderMap.computeIfAbsent(next.getId(),
                            k -> new HashMap<>());

                    /*
                     * First fetch workflow assignments. After that, if workflow metadata contains
                     * userNodes for this task and the task is not a gateway, use those holderIds.
                     */
                    if (userNodes != null && !userNodes.isEmpty() && !TYPE_GATEWAY.equals(next.getType())) {

                        applicationFlowLogs.info("Using holderIds from workflow metadata after fetching assignments. "
                                + "txnId={}, taskId={}", txnId, next.getId());

                        userNodes.stream().filter(Objects::nonNull).filter(u -> next.getId().equals(u.getTaskId()))
                                .forEach(u -> {

                                    String locationId = String.valueOf(u.getLocationId());

                                    applicationFlowLogs.info("Adding metadata holderId={} for locationId={} taskId={}",
                                            u.getHolderId(), locationId, next.getId());

                                    locationHolderMap.computeIfAbsent(locationId, k -> new ArrayList<>())
                                            .add(u.getHolderId());
                                });

                    } else {

                        /*
                         * No workflow metadata holder information available. Use holderIds returned by
                         * workflow assignment API.
                         */
                        applicationFlowLogs.info("Using holderIds from workflow assignments for txnId={}, taskId={}",
                                txnId, next.getId());

                        assignments.stream().filter(Objects::nonNull).filter(a -> a.getLocationId() != null)
                                .forEach(a -> {

                                    applicationFlowLogs.info(
                                            "Adding assignment holderId={} for locationId={} taskId={}",
                                            a.getHolderId(), a.getLocationId(), next.getId());

                                    locationHolderMap.computeIfAbsent(a.getLocationId(), k -> new ArrayList<>())
                                            .add(a.getHolderId());
                                });
                    }

                    /*
                     * Remove null and duplicate holder IDs.
                     */
                    locationHolderMap.replaceAll(
                            (locationId, holders) -> holders.stream().filter(Objects::nonNull).distinct().toList());

                    applicationFlowLogs.info("Final locationHolderMap for txnId={}, taskId={} : {}", txnId,
                            next.getId(), locationHolderMap);

                    /*
                     * Map holderIds to allowed offices.
                     */
                    location.getAllowedOffices().forEach(office -> {

                        String officeId = String.valueOf(office.getOrgUnitCode());

                        List<String> holderIds = locationHolderMap.getOrDefault(officeId, List.of());

                        office.setHolderIds(holderIds);

                        applicationFlowLogs.info("Mapped office locationId={} with holderIds={} for taskId={}",
                                officeId, holderIds, next.getId());
                    });

                    location.setAllowedOffices(allowedOffices);

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
                                           CurrentProcess currentActionProcess, String taskId, Map<String, Map<String,List<String>>> taskLocationUserHolderMap, Map<String, LocalDateTime> timerDueDate
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

    public Mono<Void> refreshTaskAvailableOfficeLocation(
            TaskAvailableOfficeLocation officeLocation,
            Map<String, Map<String, List<String>>> taskLocationUserHolderMap,
            Long sourceLocationId,
            Integer sourceLevelCode,
            Integer destinationLevelCode,
            UserSessionObject user,
            String applicationId,
            Map<String, ApplicationRouting> applicationRoutingMap,String txnId) {

        applicationFlowLogs.info(
                "Refreshing office locations for taskId={}, parentLocationId={}, sourceLevel={}, targetLocationLevel={}",
                officeLocation.getTaskId(),
                sourceLocationId,
                sourceLevelCode,
                destinationLevelCode);

        return resolveRoutingSources(
                applicationId,
                officeLocation.getTaskId(),
                applicationRoutingMap,
                sourceLevelCode,
                sourceLocationId,
                user,
                txnId)

                .flatMapMany(routingSources ->

                        Flux.fromIterable(routingSources)

                                .flatMap(routingSource -> {

                                    if (routingSource.getSourceLocationId() == null
                                            || routingSource.getSourceLevelCode() == null
                                            || destinationLevelCode == null || (routingSource.getSourceLevelCode().equals(destinationLevelCode))) {

                                        return Mono.empty();
                                    }

                                    applicationFlowLogs.info(
                                            "Resolving actual next-task locations "
                                                    + "sourceLocationId={}, sourceLevelCode={}, destinationLevelCode={}",
                                            routingSource.getSourceLocationId(),
                                            routingSource.getSourceLevelCode(),
                                            destinationLevelCode);

                                    return apiClient.getRelatedLocationIds(
                                            routingSource.getSourceLevelCode(),
                                            routingSource.getSourceLocationId(),
                                            destinationLevelCode,
                                            user);
                                })

                                .flatMapIterable(ids -> ids)

                                .filter(Objects::nonNull)

                                .distinct()

                                .collectList()

                                .map(allowedLocationIds -> {

                                    Map<String, List<String>> locationHolderMap =
                                            taskLocationUserHolderMap.getOrDefault(
                                                    officeLocation.getTaskId(),
                                                    Map.of());

                                    List<ServiceMeta.AvailableApplyLocations> filteredOffices =
                                            officeLocation.getAllowedOffices()
                                                    .stream()
                                                    .filter(office -> {

                                                        String locationId =
                                                                String.valueOf(
                                                                        office.getOrgUnitCode());

                                                        boolean locationAllowed =
                                                                allowedLocationIds.contains(
                                                                        locationId);

                                                        if (!locationAllowed) {
                                                            return false;
                                                        }

                                                        List<String> holderIds =
                                                                locationHolderMap.get(
                                                                        locationId);

                                                        if (holderIds == null
                                                                || holderIds.isEmpty()) {

                                                            return false;
                                                        }

                                                        office.setHolderIds(holderIds);

                                                        return true;
                                                    })
                                                    .toList();

                                    officeLocation.setAllowedOffices(
                                            new ArrayList<>(filteredOffices));

                                    applicationFlowLogs.info(
                                            "Final routing locations for taskId={} = {}",
                                            officeLocation.getTaskId(),
                                            officeLocation.getAllowedOffices());

                                    return officeLocation;
                                }))
                .then();
    }
//    public Mono<Void> refreshTaskAvailableOfficeLocation(
//            TaskAvailableOfficeLocation officeLocation,
//            Map<String, Map<String, List<String>>> taskLocationUserHolderMap,
//            Long sourceLocationId,
//            Integer sourceLevelCode,
//            Integer destinationLevelCode,
//            UserSessionObject user,String applicationId,
//            Map<String, ApplicationRouting> applicationRoutingMap) {
//
//        applicationFlowLogs.info(
//                "Refreshing office locations for taskId={}, parentLocationId={}, sourceLevel={}, targetLocationLevel={}",
//                officeLocation.getTaskId(),
//                sourceLocationId,
//                sourceLevelCode,
//                destinationLevelCode);
//
//        return resolveRoutingSource(
//        		applicationId,
//                officeLocation.getTaskId(),
//                applicationRoutingMap,
//                sourceLevelCode,
//                sourceLocationId,
//                user)
//
//                .flatMap(routingSource -> {
//
//                    Long resolvedSourceLocationId =
//                            routingSource.getSourceLocationId();
//
//                    Integer resolvedSourceLevelCode =
//                            routingSource.getSourceLevelCode();
//
//                    applicationFlowLogs.info(
//                            "Resolved routing source taskId={}, sourceLocationId={}, sourceLevelCode={}, destinationLevelCode={}",
//                            officeLocation.getTaskId(),
//                            resolvedSourceLocationId,
//                            resolvedSourceLevelCode,
//                            destinationLevelCode);
//
//                    if (resolvedSourceLocationId == null
//                            || resolvedSourceLevelCode == null
//                            || destinationLevelCode == null || (resolvedSourceLevelCode.equals(destinationLevelCode))) {
//
//                        applicationFlowLogs.warn(
//                                "Unable to refresh office locations. Missing routing values taskId={}, sourceLocationId={}, sourceLevelCode={}, destinationLevelCode={}",
//                                officeLocation.getTaskId(),
//                                resolvedSourceLocationId,
//                                resolvedSourceLevelCode,
//                                destinationLevelCode);
//
//                        return Mono.empty();
//                    }
//
//                    return apiClient.getRelatedLocationIds(
//                            resolvedSourceLevelCode,
//                            resolvedSourceLocationId,
//                            destinationLevelCode,
//                            user)
//
//                            .doOnNext(allowedLocationIds ->
//                                    applicationFlowLogs.info(
//                                            "SPGD returned allowed locations for parentLocationId={}, targetLevel={}: {}",
//                                            resolvedSourceLocationId,
//                                            destinationLevelCode,
//                                            allowedLocationIds))
//
//                            .map(allowedLocationIds -> {
//
//                                Map<String, List<String>> locationHolderMap =
//                                        taskLocationUserHolderMap.getOrDefault(
//                                                officeLocation.getTaskId(),
//                                                Map.of());
//
//                                applicationFlowLogs.info(
//                                        "Location holder map for taskId {} : {}",
//                                        officeLocation.getTaskId(),
//                                        locationHolderMap);
//
//                                List<ServiceMeta.AvailableApplyLocations> hierarchyFilteredOffices =
//                                        officeLocation.getAllowedOffices()
//                                                .stream()
//                                                .filter(office -> {
//
//                                                    String locationId =
//                                                            String.valueOf(
//                                                                    office.getOrgUnitCode());
//
//                                                    boolean allowed =
//                                                            allowedLocationIds.contains(
//                                                                    locationId);
//
//                                                    applicationFlowLogs.info(
//                                                            "Hierarchy filtering taskId={}, locationId={}, allowed={}",
//                                                            officeLocation.getTaskId(),
//                                                            locationId,
//                                                            allowed);
//
//                                                    return allowed;
//                                                })
//                                                .toList();
//
//                                applicationFlowLogs.info(
//                                        "Hierarchy filtered offices for taskId={} before={}, after={}",
//                                        officeLocation.getTaskId(),
//                                        officeLocation.getAllowedOffices().size(),
//                                        hierarchyFilteredOffices.size());
//
//                                List<ServiceMeta.AvailableApplyLocations> filteredOffices =
//                                        hierarchyFilteredOffices.stream()
//                                                .filter(office -> {
//
//                                                    String locationId =
//                                                            String.valueOf(
//                                                                    office.getOrgUnitCode());
//
//                                                    List<String> holderIds =
//                                                            locationHolderMap.get(
//                                                                    locationId);
//
//                                                    applicationFlowLogs.info(
//                                                            "Evaluating taskId={}, locationId={}, holderIds={}",
//                                                            officeLocation.getTaskId(),
//                                                            locationId,
//                                                            holderIds);
//
//                                                    if (holderIds == null
//                                                            || holderIds.isEmpty()) {
//
//                                                        applicationFlowLogs.info(
//                                                                "Removing locationId={} for taskId={} because no holders found",
//                                                                locationId,
//                                                                officeLocation.getTaskId());
//
//                                                        return false;
//                                                    }
//
//                                                    office.setHolderIds(holderIds);
//
//                                                    applicationFlowLogs.info(
//                                                            "Retaining locationId={} for taskId={} with holderIds={}",
//                                                            locationId,
//                                                            officeLocation.getTaskId(),
//                                                            holderIds);
//
//                                                    return true;
//
//                                                })
//                                                .toList();
//
//                                applicationFlowLogs.info(
//                                        "Final filtered offices for taskId={} before={}, after={}",
//                                        officeLocation.getTaskId(),
//                                        officeLocation.getAllowedOffices().size(),
//                                        filteredOffices.size());
//
//                                officeLocation.setAllowedOffices(
//                                        new ArrayList<>(filteredOffices));
//
//                                applicationFlowLogs.info(
//                                        "Final office locations for taskId={} : {}",
//                                        officeLocation.getTaskId(),
//                                        officeLocation.getAllowedOffices());
//
//                                return officeLocation;
//                            })
//                            .then();
//
//                });
//    }   
    
    private Mono<List<RoutingSource>> resolveRoutingSources(
            String applicationId,
            String taskId,
            Map<String, ApplicationRouting> applicationRoutingMap,
            Integer defaultSourceLevelCode,
            Long defaultSourceLocationId,
            UserSessionObject user,
            String txnId) {

        ApplicationRouting routing =
                applicationRoutingMap != null
                        ? applicationRoutingMap.get(taskId)
                        : null;

        /*
         * No routing configuration.
         */
        if (routing == null
                || !Boolean.TRUE.equals(routing.getEnabled())
                || routing.getSelectedAttributes() == null
                || routing.getSelectedAttributes().isEmpty()) {

            return Mono.just(
                    List.of(
                            new RoutingSource(
                                    defaultSourceLocationId,
                                    defaultSourceLevelCode)));
        }

        String routingMode =routing.getRoutingMode();

        if (routingMode == null || routingMode.isBlank()) {

            applicationFlowLogs.warn(
                    "Routing mode missing for taskId={}. Using default source.",
                    taskId);

            return Mono.just(
                    List.of(
                            new RoutingSource(
                                    defaultSourceLocationId,
                                    defaultSourceLevelCode)));
        }

        List<RoutingAttribute> selectedAttributes =
                routing.getSelectedAttributes()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(attribute -> attribute.getAttributeId() != null)
                        .sorted(
                                Comparator.comparing(
                                        RoutingAttribute::getPriority,
                                        Comparator.nullsLast(Integer::compareTo)))
                        .toList();

        if (selectedAttributes.isEmpty()) {

            return Mono.just(
                    List.of(
                            new RoutingSource(
                                    defaultSourceLocationId,
                                    defaultSourceLevelCode)));
        }

        applicationFlowLogs.info(
                "Resolving application routing taskId={}, mode={}, selectedAttributes={}",
                taskId,
                routingMode,
                selectedAttributes);

        switch (routingMode) {

            case "ALL_LOCATIONS":

                return resolveAllLocations(
                        applicationId,
                        selectedAttributes,
                        user,
                        txnId,
                        defaultSourceLocationId,
                        defaultSourceLevelCode);

            case "EXCLUSIVE_LOCATION":

                return resolveExclusiveLocation(
                        applicationId,
                        selectedAttributes,
                        user,
                        txnId,
                        defaultSourceLocationId,
                        defaultSourceLevelCode);

            case "INCLUSIVE_LOCATIONS":

                return resolveInclusiveLocations(
                        applicationId,
                        selectedAttributes,
                        routing.getCombinations(),
                        user,
                        txnId,
                        defaultSourceLocationId,
                        defaultSourceLevelCode);

            default:

                applicationFlowLogs.warn(
                        "Unsupported routing mode={} for taskId={}. Using default source.",
                        routingMode,
                        taskId);

                return Mono.just(
                        List.of(
                                new RoutingSource(
                                        defaultSourceLocationId,
                                        defaultSourceLevelCode)));
        }
    }
    private Mono<List<RoutingSource>> resolveAllLocations(
            String applicationId,
            List<RoutingAttribute> attributes,
            UserSessionObject user,
            String txnId,
            Long defaultSourceLocationId,
            Integer defaultSourceLevelCode) {

        return fetchRoutingAttributeValues(
                applicationId,
                attributes,
                user,
                txnId)

                .map(attributeValues -> {

                    List<RoutingSource> sources = new ArrayList<>();

                    for (RoutingAttribute attribute : attributes) {

                        Object value =
                                attributeValues.get(attribute.getAttributeId());

                        if (!hasRoutingValue(value)) {
                            continue;
                        }

                        Long locationId = parseLocationId(
                                value,
                                attribute.getAttributeId(),
                                txnId);

                        Integer levelCode =
                                attribute.getHierarchyLevel();

                        if (locationId == null || levelCode == null) {
                            continue;
                        }

                        sources.add(
                                new RoutingSource(
                                        locationId,
                                        levelCode));
                    }

                    if (sources.isEmpty()) {

                        sources.add(
                                new RoutingSource(
                                        defaultSourceLocationId,
                                        defaultSourceLevelCode));
                    }

                    applicationFlowLogs.info(
                            "ALL_LOCATIONS resolved routing sources={}",
                            sources);

                    return sources;
                });
    }
    
    private Mono<List<RoutingSource>> resolveExclusiveLocation(
            String applicationId,
            List<RoutingAttribute> attributes,
            UserSessionObject user,
            String txnId,
            Long defaultSourceLocationId,
            Integer defaultSourceLevelCode) {

        return fetchRoutingAttributeValues(
                applicationId,
                attributes,
                user,
                txnId)

                .map(attributeValues -> {

                    for (RoutingAttribute attribute : attributes) {

                        Object value =
                                attributeValues.get(
                                        attribute.getAttributeId());

                        if (!hasRoutingValue(value)) {
                            continue;
                        }

                        Long locationId =
                                parseLocationId(
                                        value,
                                        attribute.getAttributeId(),
                                        txnId);

                        Integer levelCode =
                                attribute.getHierarchyLevel();

                        if (locationId == null || levelCode == null) {
                            continue;
                        }

                        applicationFlowLogs.info(
                                "EXCLUSIVE_LOCATION matched priority={}, attributeId={}, locationId={}, levelCode={}",
                                attribute.getPriority(),
                                attribute.getAttributeId(),
                                locationId,
                                levelCode);

                        return List.of(
                                new RoutingSource(
                                        locationId,
                                        levelCode));
                    }

                    return List.of(
                            new RoutingSource(
                                    defaultSourceLocationId,
                                    defaultSourceLevelCode));
                });
    }
    
    private Mono<List<RoutingSource>> resolveInclusiveLocations(
            String applicationId,
            List<RoutingAttribute> selectedAttributes,
            List<ApplicationRouting.RoutingCombination> combinations,
            UserSessionObject user,
            String txnId,
            Long defaultSourceLocationId,
            Integer defaultSourceLevelCode) {

        if (combinations == null || combinations.isEmpty()) {

            applicationFlowLogs.warn(
                    "No inclusive routing combinations configured. Using default source.");

            return Mono.just(
                    List.of(
                            new RoutingSource(
                                    defaultSourceLocationId,
                                    defaultSourceLevelCode)));
        }

        List<ApplicationRouting.RoutingCombination> sortedCombinations =
                combinations.stream()
                        .filter(Objects::nonNull)
                        .filter(c -> c.getSourceAttributeIds() != null
                                && !c.getSourceAttributeIds().isEmpty())
                        .filter(c -> c.getDestinationAttributeIds() != null
                                && !c.getDestinationAttributeIds().isEmpty())
                        .sorted(
                                Comparator.comparing(
                                        ApplicationRouting.RoutingCombination::getPriority,
                                        Comparator.nullsLast(Integer::compareTo)))
                        .toList();

        if (sortedCombinations.isEmpty()) {

            return Mono.just(
                    List.of(
                            new RoutingSource(
                                    defaultSourceLocationId,
                                    defaultSourceLevelCode)));
        }

        /*
         * Fetch values for all attributes referenced by all combinations.
         */
        List<RoutingAttribute> requiredAttributes =
                selectedAttributes.stream()
                        .filter(attribute ->
                                sortedCombinations.stream().anyMatch(
                                        combination ->
                                                combination.getSourceAttributeIds()
                                                        .contains(attribute.getAttributeId())))
                        .toList();

        return fetchRoutingAttributeValues(
                applicationId,
                requiredAttributes,
                user,
                txnId)

                .flatMap(attributeValues ->
                        Flux.fromIterable(sortedCombinations)

                                .concatMap(combination ->
                                        resolveInclusiveCombination(
                                                combination,
                                                selectedAttributes,
                                                attributeValues,
                                                user,
                                                txnId))

                                .collectList())

                .map(allCombinationSources -> {

                    List<RoutingSource> result =
                            allCombinationSources.stream()
                                    .flatMap(List::stream)
                                    .filter(Objects::nonNull)
                                    .distinct()
                                    .toList();

                    if (result.isEmpty()) {

                        return List.of(
                                new RoutingSource(
                                        defaultSourceLocationId,
                                        defaultSourceLevelCode));
                    }

                    return result;
                });
    }
    
    private Mono<List<RoutingSource>> resolveInclusiveCombination(
            ApplicationRouting.RoutingCombination combination,
            List<RoutingAttribute> selectedAttributes,
            Map<String, Object> attributeValues,
            UserSessionObject user,
            String txnId) {

        List<RoutingAttribute> sourceAttributes =
                selectedAttributes.stream()
                        .filter(attribute ->
                                combination.getSourceAttributeIds()
                                        .contains(attribute.getAttributeId()))
                        .toList();

        List<RoutingAttribute> destinationAttributes =
                selectedAttributes.stream()
                        .filter(attribute ->
                                combination.getDestinationAttributeIds()
                                        .contains(attribute.getAttributeId()))
                        .toList();

        if (sourceAttributes.isEmpty()
                || destinationAttributes.isEmpty()) {

            return Mono.just(Collections.emptyList());
        }

        applicationFlowLogs.info(
                "Resolving inclusive combination priority={}, sourceAttributeIds={}, destinationAttributeIds={}",
                combination.getPriority(),
                combination.getSourceAttributeIds(),
                combination.getDestinationAttributeIds());

        // Use only the first destination attribute
        RoutingAttribute destinationAttribute =
                destinationAttributes.get(0);

        Integer destinationLevel =
                destinationAttribute.getHierarchyLevel();

        if (destinationLevel == null) {

            applicationFlowLogs.warn(
                    "Destination hierarchy level is null for attributeId={}",
                    destinationAttribute.getAttributeId());

            return Mono.just(Collections.emptyList());
        }

        return Flux.fromIterable(sourceAttributes)
                .flatMap(sourceAttribute -> {

                    Object value =
                            attributeValues.get(
                                    sourceAttribute.getAttributeId());

                    if (!hasRoutingValue(value)) {

                        applicationFlowLogs.warn(
                                "No routing value found for source attributeId={}",
                                sourceAttribute.getAttributeId());

                        return Mono.<List<String>>empty();
                    }

                    Long sourceLocationId =
                            parseLocationId(
                                    value,
                                    sourceAttribute.getAttributeId(),
                                    txnId);

                    Integer sourceLevel =
                            sourceAttribute.getHierarchyLevel();

                    if (sourceLocationId == null
                            || sourceLevel == null) {

                        applicationFlowLogs.warn(
                                "Invalid routing source for attributeId={}, sourceLocationId={}, sourceLevel={}",
                                sourceAttribute.getAttributeId(),
                                sourceLocationId,
                                sourceLevel);

                        return Mono.<List<String>>empty();
                    }

                    applicationFlowLogs.info(
                            "Inclusive stage-1 related location: "
                                    + "sourceAttribute={}, sourceLocationId={}, sourceLevel={}, "
                                    + "destinationAttribute={}, destinationLevel={}",
                            sourceAttribute.getAttributeId(),
                            sourceLocationId,
                            sourceLevel,
                            destinationAttribute.getAttributeId(),
                            destinationLevel);

                    return apiClient.getRelatedLocationIds(
                            sourceLevel,
                            sourceLocationId,
                            destinationLevel,
                            user);
                })
                .collectList()

                // Intersect the destination locations returned
                // for all source attributes
                .map(this::intersectRelatedLocations)

                // Convert resolved locations into RoutingSource
                .map(resolvedDestinationLocations ->
                        resolvedDestinationLocations.stream()
                                .map(locationId ->
                                        new RoutingSource(
                                                Long.valueOf(locationId),
                                                destinationLevel))
                                .toList());
    }
    private Long parseLocationId(
            Object value,
            String attributeId,
            String txnId) {

        if (value == null) {
            return null;
        }

        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {

            applicationFlowLogs.error(
                    "Invalid routing location value={}, attributeId={}",
                    value,
                    attributeId);

            throw new SPRuntimeError(
                    "Invalid application routing location",
                    HttpStatus.BAD_REQUEST,
                    txnId);
        }
    }
    private List<String> intersectRelatedLocations(
            List<Collection<String>> relatedLocationLists) {

        if (relatedLocationLists == null
                || relatedLocationLists.isEmpty()) {

            return Collections.emptyList();
        }

        Set<String> intersection =
                new HashSet<>(relatedLocationLists.get(0));

        for (int i = 1; i < relatedLocationLists.size(); i++) {

            intersection.retainAll(
                    relatedLocationLists.get(i));

            if (intersection.isEmpty()) {
                break;
            }
        }

        return new ArrayList<>(intersection);
    }
    
    private Mono<Map<String, Object>> fetchRoutingAttributeValues(
            String applicationId,
            List<RoutingAttribute> attributes,
            UserSessionObject user,
            String txnId) {

        Map<String, List<RoutingAttribute>> grouped =
                attributes.stream()
                        .filter(Objects::nonNull)
                        .filter(attribute ->
                                attribute.getAttributeKey() != null
                                        && !attribute.getAttributeKey().isBlank())
                        .collect(Collectors.groupingBy(
                                RoutingAttribute::getAttributeKey));

        return Flux.fromIterable(grouped.values())

                .flatMap(group -> {

                    RoutingAttribute first =
                            group.get(0);

                    String[] parts =
                            first.getAttributeKey()
                                    .split("\\$");

                    if (parts.length != 3) {

                        return Mono.error(
                                new SPRuntimeError(
                                        "Invalid application routing attributeKey",
                                        HttpStatus.BAD_REQUEST,
                                        txnId));
                    }

                    String taskId = parts[0];
                    String holderId = parts[1];

                    List<String> attributeIds =
                            group.stream()
                                    .map(RoutingAttribute::getAttributeId)
                                    .filter(Objects::nonNull)
                                    .distinct()
                                    .toList();

                    return apiClient.fetchApplicationAttributeValues(
                            applicationId,
                            taskId,
                            holderId,
                            attributeIds,
                            user);
                })

                .reduce(
                        new HashMap<>(),
                        (allValues, currentValues) -> {
                            if (currentValues != null) {
                                allValues.putAll(currentValues);
                            }
                            return allValues;
                        });
    }

	private boolean hasRoutingValue(Object value) {
		if (value == null) {
			return false;
		}

		if (value instanceof String) {
			return !((String) value).isBlank();
		}

		return true;
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
