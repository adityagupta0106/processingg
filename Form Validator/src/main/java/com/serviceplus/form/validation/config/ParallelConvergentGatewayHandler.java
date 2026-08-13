package com.serviceplus.form.validation.config;

import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_PARALLEL_CONVERGENT;

import java.util.List;
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
public class ParallelConvergentGatewayHandler implements ConvergentGatewayHandler {

	private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

	private final CurrentProcessRepository currentProcessRepository;

	public ParallelConvergentGatewayHandler(CurrentProcessRepository currentProcessRepository) {
		this.currentProcessRepository = currentProcessRepository;
	}

	@Override
	public boolean supports(String gatewayType) {

		boolean supported = GATEWAY_BEHAVIOUR_PARALLEL_CONVERGENT.equalsIgnoreCase(gatewayType);

		applicationFlowLogs.debug("Checking Parallel Convergent Gateway support. gatewayType={}, supported={}",
				gatewayType, supported);

		return supported;
	}

	@Override
	public Mono<Boolean> canProceed(CurrentProcess gatewayProcess, CurrentProcess currentActionProcess,
			TaskRelationDTO taskRelation, String applicationId, Integer serviceId, String tenantId) {

		applicationFlowLogs.info(
				"Starting Parallel Convergent Gateway validation. "
						+ "gatewayProcessId={}, currentTask={}, previousTask={}, "
						+ "applicationId={}, serviceId={}, tenantId={}",
				gatewayProcess != null ? gatewayProcess.getProcessId() : null,
				gatewayProcess != null ? gatewayProcess.getCurrentTask() : null,
				gatewayProcess != null ? gatewayProcess.getPreviousTask() : null, applicationId, serviceId, tenantId);

		if (taskRelation == null) {

			applicationFlowLogs.warn(
					"Task relation is null for Parallel Convergent Gateway. " + "gatewayProcessId={}, applicationId={}",
					gatewayProcess != null ? gatewayProcess.getProcessId() : null, applicationId);

			return Mono.just(false);
		}

		List<String> previousTasks = taskRelation.getPreviousTask();

		applicationFlowLogs.info("Parallel Convergent Gateway previous tasks={}", previousTasks);

		if (previousTasks == null || previousTasks.isEmpty()) {

			applicationFlowLogs.warn(
					"No previous tasks configured for Parallel Convergent Gateway. "
							+ "gatewayProcessId={}, applicationId={}",
					gatewayProcess != null ? gatewayProcess.getProcessId() : null, applicationId);

			return Mono.just(false);
		}

		List<String> distinctPreviousTasks = previousTasks.stream().filter(task -> task != null && !task.isBlank())
				.distinct().toList();

		applicationFlowLogs.info("Parallel Convergent Gateway distinct previous tasks={}, expectedCount={}",
				distinctPreviousTasks, distinctPreviousTasks.size());

		String triggeringProcessId = gatewayProcess != null ? gatewayProcess.getPreviousProcessId() : null;

		applicationFlowLogs.info(
				"Parallel Convergent Gateway correlation. " + "gatewayProcessId={}, triggeringProcessId={}",
				gatewayProcess != null ? gatewayProcess.getProcessId() : null, triggeringProcessId);

		if (triggeringProcessId == null || triggeringProcessId.isBlank()) {

			applicationFlowLogs.warn(
					"Unable to determine triggering process for Parallel "
							+ "Convergent Gateway. gatewayProcessId={}, applicationId={}",
					gatewayProcess != null ? gatewayProcess.getProcessId() : null, applicationId);

			return Mono.just(false);
		}

		/*
		 * Find the process of the branch which triggered the convergent gateway.
		 */
		return currentProcessRepository
				.findByProcessIdAndApplicationIdAndTenantId(triggeringProcessId, applicationId, tenantId)

				.flatMap(triggeringProcess -> {

					String parallelGatewayProcessId = triggeringProcess.getPreviousProcessId();

					applicationFlowLogs.info("Resolved Parallel Gateway execution process. "
							+ "triggeringProcessId={}, parallelGatewayProcessId={}, " + "expectedPreviousTasks={}",
							triggeringProcess.getProcessId(), parallelGatewayProcessId, distinctPreviousTasks);

					if (parallelGatewayProcessId == null || parallelGatewayProcessId.isBlank()) {

						applicationFlowLogs.warn(
								"Unable to resolve Parallel Gateway execution process ID. "
										+ "triggeringProcessId={}, applicationId={}",
								triggeringProcess.getProcessId(), applicationId);

						return Mono.just(false);
					}

					/*
					 * Fetch only the branches belonging to THIS Parallel Gateway execution.
					 *
					 * This prevents previous executions of the same workflow from being considered.
					 */
					return currentProcessRepository
							.findByServiceIdAndApplicationIdAndPreviousProcessIdAndCurrentTaskInAndTenantId(serviceId,
									applicationId, parallelGatewayProcessId, distinctPreviousTasks, tenantId)

							.doOnNext(cp -> applicationFlowLogs.info(
									"Parallel branch process found. "
											+ "processId={}, currentTask={}, previousTask={}, "
											+ "previousProcessId={}, actionTaken={}",
									cp.getProcessId(), cp.getCurrentTask(), cp.getPreviousTask(),
									cp.getPreviousProcessId(), cp.getActionTaken()))

							
							.filter(cp -> {

								boolean completed = "Y".equalsIgnoreCase(cp.getActionTaken());

								applicationFlowLogs.debug(
										"Checking parallel branch completion. "
												+ "processId={}, currentTask={}, actionTaken={}, completed={}",
										cp.getProcessId(), cp.getCurrentTask(), cp.getActionTaken(), completed);

								return completed;
							})

							.map(CurrentProcess::getCurrentTask)

							.distinct()

							.collect(Collectors.toSet())

							.map(completedTasks -> {

								if (currentActionProcess != null && currentActionProcess.getCurrentTask() != null
										&& distinctPreviousTasks.contains(currentActionProcess.getCurrentTask())) {

									applicationFlowLogs.info(
											"Adding current action process as completed branch. "
													+ "processId={}, currentTask={}, dbActionTaken={}",
											currentActionProcess.getProcessId(), currentActionProcess.getCurrentTask(),
											currentActionProcess.getActionTaken());

									completedTasks.add(currentActionProcess.getCurrentTask());
								}

								boolean allCompleted = completedTasks.size() == distinctPreviousTasks.size()
										&& completedTasks.containsAll(distinctPreviousTasks);

								applicationFlowLogs.info(
										"Parallel Convergent Gateway result. "
												+ "parallelGatewayProcessId={}, expectedTasks={}, "
												+ "completedTasks={}, expectedCount={}, completedCount={}, "
												+ "allCompleted={}",
										parallelGatewayProcessId, distinctPreviousTasks, completedTasks,
										distinctPreviousTasks.size(), completedTasks.size(), allCompleted);

								return allCompleted;
							});
				})

				.defaultIfEmpty(false)

				.doOnSuccess(result -> applicationFlowLogs.info(
						"Parallel Convergent Gateway validation completed. " + "gatewayProcessId={}, result={}",
						gatewayProcess != null ? gatewayProcess.getProcessId() : null, result))

				.doOnError(error -> applicationFlowLogs.error(
						"Error while validating Parallel Convergent Gateway. "
								+ "gatewayProcessId={}, applicationId={}, serviceId={}",
						gatewayProcess != null ? gatewayProcess.getProcessId() : null, applicationId, serviceId,
						error));
	}
}
