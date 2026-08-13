package com.serviceplus.form.validation.config;

import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_INCLUSIVE_CONVERGENT;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.TaskRelationDTO;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.handlers.ConvergentGatewayHandler;
import com.serviceplus.form.validation.repository.CurrentProcessRepository;

import reactor.core.publisher.Mono;

@Component
public class InclusiveConvergentGatewayHandler implements ConvergentGatewayHandler {

	private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

	private final CurrentProcessRepository currentProcessRepository;

	public InclusiveConvergentGatewayHandler(CurrentProcessRepository currentProcessRepository) {
		this.currentProcessRepository = currentProcessRepository;
	}

	@Override
	public boolean supports(String gatewayType) {

		return GATEWAY_BEHAVIOUR_INCLUSIVE_CONVERGENT.equalsIgnoreCase(gatewayType);
	}

	@Override
	public Mono<Boolean> canProceed(CurrentProcess gatewayProcess, CurrentProcess currentActionProcess,
			TaskRelationDTO taskRelation, String applicationId, Integer serviceId, String tenantId) {

		applicationFlowLogs.info(
				"Starting Inclusive Convergent Gateway validation. "
						+ "gatewayProcessId={}, currentActionProcessId={}, "
						+ "currentTask={}, applicationId={}, serviceId={}, tenantId={}",
				gatewayProcess != null ? gatewayProcess.getProcessId() : null,
				currentActionProcess != null ? currentActionProcess.getProcessId() : null,
				currentActionProcess != null ? currentActionProcess.getCurrentTask() : null, applicationId, serviceId,
				tenantId);

		if (taskRelation == null) {

			applicationFlowLogs.warn(
					"Task relation is null for Inclusive Convergent Gateway. gatewayProcessId={}, applicationId={}",
					gatewayProcess != null ? gatewayProcess.getProcessId() : null, applicationId);

			return Mono.just(false);
		}

		List<String> previousTasks = taskRelation.getPreviousTask();

		if (previousTasks == null || previousTasks.isEmpty()) {

			applicationFlowLogs.warn(
					"No previous tasks configured for Inclusive Convergent Gateway. "
							+ "gatewayProcessId={}, applicationId={}",
					gatewayProcess != null ? gatewayProcess.getProcessId() : null, applicationId);

			return Mono.just(false);
		}

		List<String> distinctPreviousTasks = previousTasks.stream().filter(task -> task != null && !task.isBlank())
				.distinct().toList();

		applicationFlowLogs.info("Inclusive Convergent Gateway previous tasks={}, expectedCount={}",
				distinctPreviousTasks, distinctPreviousTasks.size());

		String triggeringProcessId = gatewayProcess != null ? gatewayProcess.getPreviousProcessId() : null;

		applicationFlowLogs.info(
				"Inclusive Convergent Gateway correlation. " + "gatewayProcessId={}, triggeringProcessId={}",
				gatewayProcess != null ? gatewayProcess.getProcessId() : null, triggeringProcessId);

		if (triggeringProcessId == null || triggeringProcessId.isBlank()) {

			applicationFlowLogs.warn(
					"Unable to determine triggering process for Inclusive "
							+ "Convergent Gateway. gatewayProcessId={}, applicationId={}",
					gatewayProcess != null ? gatewayProcess.getProcessId() : null, applicationId);

			return Mono.just(false);
		}

		return currentProcessRepository
				.findByProcessIdAndApplicationIdAndTenantId(triggeringProcessId, applicationId, tenantId)

				.doOnNext(triggeringProcess -> applicationFlowLogs.info(
						"Triggering branch process found. " + "processId={}, currentTask={}, previousTask={}, "
								+ "previousProcessId={}, actionTaken={}",
						triggeringProcess.getProcessId(), triggeringProcess.getCurrentTask(),
						triggeringProcess.getPreviousTask(), triggeringProcess.getPreviousProcessId(),
						triggeringProcess.getActionTaken()))

				.flatMap(triggeringProcess -> {

					String inclusiveGatewayProcessId = triggeringProcess.getPreviousProcessId();

					applicationFlowLogs.info("Resolved Inclusive Gateway execution process. "
							+ "triggeringProcessId={}, inclusiveGatewayProcessId={}, " + "expectedPreviousTasks={}",
							triggeringProcess.getProcessId(), inclusiveGatewayProcessId, distinctPreviousTasks);

					if (inclusiveGatewayProcessId == null || inclusiveGatewayProcessId.isBlank()) {

						applicationFlowLogs.warn(
								"Unable to resolve Inclusive Gateway execution process ID. "
										+ "triggeringProcessId={}, applicationId={}",
								triggeringProcess.getProcessId(), applicationId);

						return Mono.just(false);
					}

					return currentProcessRepository
							.findByServiceIdAndApplicationIdAndPreviousProcessIdAndCurrentTaskInAndTenantId(serviceId,
									applicationId, inclusiveGatewayProcessId, distinctPreviousTasks, tenantId)

							.doOnNext(cp -> applicationFlowLogs.info(
									"Inclusive branch process found. "
											+ "processId={}, currentTask={}, previousTask={}, "
											+ "previousProcessId={}, actionTaken={}",
									cp.getProcessId(), cp.getCurrentTask(), cp.getPreviousTask(),
									cp.getPreviousProcessId(), cp.getActionTaken()))

							.collectList()

							.map(processes -> {

								Set<String> activatedTasks = processes.stream().map(CurrentProcess::getCurrentTask)
										.filter(distinctPreviousTasks::contains).collect(Collectors.toSet());

								Set<String> completedTasks = processes.stream()
										.filter(cp -> "Y".equalsIgnoreCase(cp.getActionTaken()))
										.map(CurrentProcess::getCurrentTask).filter(distinctPreviousTasks::contains)
										.collect(Collectors.toSet());

								if (currentActionProcess != null && currentActionProcess.getCurrentTask() != null
										&& distinctPreviousTasks.contains(currentActionProcess.getCurrentTask())) {

									String currentTask = currentActionProcess.getCurrentTask();

									applicationFlowLogs.info(
											"Adding current action process to Inclusive Gateway. "
													+ "processId={}, currentTask={}, dbActionTaken={}",
											currentActionProcess.getProcessId(), currentTask,
											currentActionProcess.getActionTaken());

									activatedTasks.add(currentTask);
									completedTasks.add(currentTask);
								}

								boolean allActivatedBranchesCompleted = !activatedTasks.isEmpty()
										&& completedTasks.containsAll(activatedTasks);

								applicationFlowLogs.info(
										"Inclusive Convergent Gateway result. " + "inclusiveGatewayProcessId={}, "
												+ "configuredTasks={}, " + "activatedTasks={}, " + "completedTasks={}, "
												+ "activatedCount={}, " + "completedCount={}, "
												+ "allActivatedBranchesCompleted={}",
										inclusiveGatewayProcessId, distinctPreviousTasks, activatedTasks,
										completedTasks, activatedTasks.size(), completedTasks.size(),
										allActivatedBranchesCompleted);

								return allActivatedBranchesCompleted;
							});
				})

				.defaultIfEmpty(false)

				.doOnSuccess(result -> applicationFlowLogs.info(
						"Inclusive Convergent Gateway validation completed. " + "gatewayProcessId={}, result={}",
						gatewayProcess != null ? gatewayProcess.getProcessId() : null, result))

				.doOnError(error -> applicationFlowLogs.error(
						"Error while validating Inclusive Convergent Gateway. "
								+ "gatewayProcessId={}, applicationId={}, serviceId={}",
						gatewayProcess != null ? gatewayProcess.getProcessId() : null, applicationId, serviceId,
						error));
	}
}