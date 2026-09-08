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
            UserSessionObject user,String applicationId,
            Map<String, ApplicationRouting> applicationRoutingMap) {

        applicationFlowLogs.info(
                "Refreshing office locations for taskId={}, parentLocationId={}, sourceLevel={}, targetLocationLevel={}",
                officeLocation.getTaskId(),
                sourceLocationId,
                sourceLevelCode,
                destinationLevelCode);

        return resolveRoutingSource(
        		applicationId,
                officeLocation.getTaskId(),
                applicationRoutingMap,
                sourceLevelCode,
                sourceLocationId,
                user)

                .flatMap(routingSource -> {

                    Long resolvedSourceLocationId =
                            routingSource.getSourceLocationId();

                    Integer resolvedSourceLevelCode =
                            routingSource.getSourceLevelCode();

                    applicationFlowLogs.info(
                            "Resolved routing source taskId={}, sourceLocationId={}, sourceLevelCode={}, destinationLevelCode={}",
                            officeLocation.getTaskId(),
                            resolvedSourceLocationId,
                            resolvedSourceLevelCode,
                            destinationLevelCode);

                    if (resolvedSourceLocationId == null
                            || resolvedSourceLevelCode == null
                            || destinationLevelCode == null) {

                        applicationFlowLogs.warn(
                                "Unable to refresh office locations. Missing routing values taskId={}, sourceLocationId={}, sourceLevelCode={}, destinationLevelCode={}",
                                officeLocation.getTaskId(),
                                resolvedSourceLocationId,
                                resolvedSourceLevelCode,
                                destinationLevelCode);

                        return Mono.empty();
                    }

                    return apiClient.getRelatedLocationIds(
                            resolvedSourceLevelCode,
                            resolvedSourceLocationId,
                            destinationLevelCode,
                            user)

                            .doOnNext(allowedLocationIds ->
                                    applicationFlowLogs.info(
                                            "SPGD returned allowed locations for parentLocationId={}, targetLevel={}: {}",
                                            resolvedSourceLocationId,
                                            destinationLevelCode,
                                            allowedLocationIds))

                            .map(allowedLocationIds -> {

                                Map<String, List<String>> locationHolderMap =
                                        taskLocationUserHolderMap.getOrDefault(
                                                officeLocation.getTaskId(),
                                                Map.of());

                                applicationFlowLogs.info(
                                        "Location holder map for taskId {} : {}",
                                        officeLocation.getTaskId(),
                                        locationHolderMap);

                                List<ServiceMeta.AvailableApplyLocations> hierarchyFilteredOffices =
                                        officeLocation.getAllowedOffices()
                                                .stream()
                                                .filter(office -> {

                                                    String locationId =
                                                            String.valueOf(
                                                                    office.getOrgUnitCode());

                                                    boolean allowed =
                                                            allowedLocationIds.contains(
                                                                    locationId);

                                                    applicationFlowLogs.info(
                                                            "Hierarchy filtering taskId={}, locationId={}, allowed={}",
                                                            officeLocation.getTaskId(),
                                                            locationId,
                                                            allowed);

                                                    return allowed;
                                                })
                                                .toList();

                                applicationFlowLogs.info(
                                        "Hierarchy filtered offices for taskId={} before={}, after={}",
                                        officeLocation.getTaskId(),
                                        officeLocation.getAllowedOffices().size(),
                                        hierarchyFilteredOffices.size());

                                List<ServiceMeta.AvailableApplyLocations> filteredOffices =
                                        hierarchyFilteredOffices.stream()
                                                .filter(office -> {

                                                    String locationId =
                                                            String.valueOf(
                                                                    office.getOrgUnitCode());

                                                    List<String> holderIds =
                                                            locationHolderMap.get(
                                                                    locationId);

                                                    applicationFlowLogs.info(
                                                            "Evaluating taskId={}, locationId={}, holderIds={}",
                                                            officeLocation.getTaskId(),
                                                            locationId,
                                                            holderIds);

                                                    if (holderIds == null
                                                            || holderIds.isEmpty()) {

                                                        applicationFlowLogs.info(
                                                                "Removing locationId={} for taskId={} because no holders found",
                                                                locationId,
                                                                officeLocation.getTaskId());

                                                        return false;
                                                    }

                                                    office.setHolderIds(holderIds);

                                                    applicationFlowLogs.info(
                                                            "Retaining locationId={} for taskId={} with holderIds={}",
                                                            locationId,
                                                            officeLocation.getTaskId(),
                                                            holderIds);

                                                    return true;

                                                })
                                                .toList();

                                applicationFlowLogs.info(
                                        "Final filtered offices for taskId={} before={}, after={}",
                                        officeLocation.getTaskId(),
                                        officeLocation.getAllowedOffices().size(),
                                        filteredOffices.size());

                                officeLocation.setAllowedOffices(
                                        new ArrayList<>(filteredOffices));

                                applicationFlowLogs.info(
                                        "Final office locations for taskId={} : {}",
                                        officeLocation.getTaskId(),
                                        officeLocation.getAllowedOffices());

                                return officeLocation;
                            })
                            .then();

                });
    }   
    
    private Mono<RoutingSource> resolveRoutingSource(
    		String applicationId,
    		String taskId,
    		Map<String, ApplicationRouting> applicationRoutingMap,
    		Integer defaultSourceLevelCode,
    		Long defaultSourceLocationId,
			UserSessionObject user) {

		ApplicationRouting routing = applicationRoutingMap != null ? applicationRoutingMap.get(taskId) : null;

		/*
		 * No application routing configured. Continue with existing/default routing.
		 */
		if (routing == null || !Boolean.TRUE.equals(routing.getEnabled()) || routing.getSelectedAttributes() == null
				|| routing.getSelectedAttributes().isEmpty()) {

			applicationFlowLogs.info("Application routing not enabled/configured for taskId={}. Using default source.",
					taskId);

			return Mono.just(new RoutingSource(defaultSourceLocationId, defaultSourceLevelCode));
		}

		/*
		 * Sort selected attributes by priority.
		 */
		List<RoutingAttribute> selectedAttributes = routing.getSelectedAttributes().stream().filter(Objects::nonNull)
				.filter(attribute -> attribute.getAttributeId() != null)
				.sorted(Comparator.comparing(RoutingAttribute::getPriority, Comparator.nullsLast(Integer::compareTo)))
				.toList();

		if (selectedAttributes.isEmpty()) {

			return Mono.just(new RoutingSource(defaultSourceLocationId, defaultSourceLevelCode));
		}

		/*
		 * Routing must be combination based.
		 */
		List<ApplicationRouting.RoutingCombination> combinations = routing.getCombinations() == null
				? Collections.emptyList()
				: routing.getCombinations().stream().filter(Objects::nonNull)
						.filter(combination -> combination.getAttributeIds() != null
								&& !combination.getAttributeIds().isEmpty())
						.sorted(Comparator.comparing(ApplicationRouting.RoutingCombination::getPriority,
								Comparator.nullsLast(Integer::compareTo)))
						.toList();

		if (combinations.isEmpty()) {

			applicationFlowLogs.warn("No routing combinations configured for taskId={}. Using default source.", taskId);

			return Mono.just(new RoutingSource(defaultSourceLocationId, defaultSourceLevelCode));
		}

		/*
		 * Collect all attribute IDs needed for all combinations.
		 */
		List<String> attributeIds = combinations.stream().flatMap(combination -> combination.getAttributeIds().stream())
				.filter(Objects::nonNull).filter(attributeId -> !attributeId.isBlank()).distinct().toList();

		if (attributeIds.isEmpty()) {

			return Mono.just(new RoutingSource(defaultSourceLocationId, defaultSourceLevelCode));
		}

		/*
		 * The Form Management API requires taskId + holderId.
		 *
		 * Current routing configuration uses:
		 *
		 * taskId$holderId$attributeId
		 *
		 * in attributeKey.
		 *
		 * Therefore resolve those values from the first configured routing attribute
		 * that contains the encoded key.
		 */
		RoutingAttribute requestAttribute = selectedAttributes.stream()
				.filter(attribute -> attribute.getAttributeKey() != null && !attribute.getAttributeKey().isBlank())
				.findFirst().orElse(null);

		if (requestAttribute == null) {

			applicationFlowLogs
					.warn("No attributeKey available for application routing taskId={}. Using default source.", taskId);

			return Mono.just(new RoutingSource(defaultSourceLocationId, defaultSourceLevelCode));
		}

		String attributeKey = requestAttribute.getAttributeKey();

		String[] parts = attributeKey.split("\\$");

		if (parts.length != 3) {

			applicationFlowLogs.warn(
					"Invalid application routing attributeKey={} for taskId={}. Expected taskId$holderId$attributeId.",
					attributeKey, taskId);

			return Mono.just(new RoutingSource(defaultSourceLocationId, defaultSourceLevelCode));
		}

		String routingTaskId = parts[0];
		String holderId = parts[1];

		applicationFlowLogs.info(
				"Fetching application routing values applicationId={}, routingTaskId={}, holderId={}, attributeIds={}",
				applicationId, routingTaskId, holderId, attributeIds);

		return apiClient.fetchApplicationAttributeValues(applicationId, routingTaskId, holderId, attributeIds, user)

				.flatMap(attributeValues -> {

					if (attributeValues == null || attributeValues.isEmpty()) {

						applicationFlowLogs.info(
								"No application attribute values found for applicationId={}, taskId={}", applicationId,
								taskId);

						return Mono.just(new RoutingSource(defaultSourceLocationId, defaultSourceLevelCode));
					}

					/*
					 * Evaluate combinations according to priority.
					 */
					for (ApplicationRouting.RoutingCombination combination : combinations) {

						List<String> combinationAttributeIds = combination.getAttributeIds();

						boolean matched = combinationAttributeIds.stream()
								.allMatch(attributeId -> hasRoutingValue(attributeValues.get(attributeId)));

						applicationFlowLogs.info(
								"Routing combination evaluated taskId={}, priority={}, attributeIds={}, matched={}",
								taskId, combination.getPriority(), combinationAttributeIds, matched);

						if (!matched) {
							continue;
						}

						/*
						 * From the matched combination select the deepest hierarchy attribute.
						 */
						RoutingAttribute targetAttribute = selectedAttributes.stream()
								.filter(attribute -> combinationAttributeIds.contains(attribute.getAttributeId()))
								.filter(attribute -> hasRoutingValue(attributeValues.get(attribute.getAttributeId())))
								.max(Comparator.comparing(RoutingAttribute::getHierarchyLevel,
										Comparator.nullsLast(Integer::compareTo)))
								.orElse(null);

						if (targetAttribute == null) {
							continue;
						}

						Object attributeValue = attributeValues.get(targetAttribute.getAttributeId());

						Long resolvedSourceLocationId;

						try {

							resolvedSourceLocationId = Long.valueOf(String.valueOf(attributeValue));

						} catch (NumberFormatException e) {

							applicationFlowLogs.error(
									"Invalid routing location value={}, attributeId={}, applicationId={}",
									attributeValue, targetAttribute.getAttributeId(), applicationId);

							return Mono.error(new SPRuntimeError("Invalid application routing location",
									HttpStatus.BAD_REQUEST, null));
						}

						Integer resolvedSourceLevelCode = targetAttribute.getHierarchyLevel();

						applicationFlowLogs.info(
								"Application routing matched taskId={}, combinationPriority={}, attributeId={}, value={}, sourceLocationId={}, sourceLevelCode={}",
								taskId, combination.getPriority(), targetAttribute.getAttributeId(), attributeValue,
								resolvedSourceLocationId, resolvedSourceLevelCode);

						return Mono.just(new RoutingSource(resolvedSourceLocationId, resolvedSourceLevelCode));
					}

					/*
					 * No combination matched. Fall back to existing/default routing.
					 */
					applicationFlowLogs.info("No routing combination matched for taskId={}. Using default source.",
							taskId);

					return Mono.just(new RoutingSource(defaultSourceLocationId, defaultSourceLevelCode));
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
