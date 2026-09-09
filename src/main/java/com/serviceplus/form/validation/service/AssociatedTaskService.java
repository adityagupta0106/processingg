package com.serviceplus.form.validation.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.executor.AssociatedTaskExecutor;

@Service
public class AssociatedTaskService {
	
	private static final Logger log = LogManager.getLogger("associateTaskLogger");

	private final Executor executor;
	
	private final ReactiveApiClient apiClient;

	private final Map<String, AssociatedTaskExecutor> executorMap;

	public AssociatedTaskService(
			@Qualifier("associatedTaskExecutor") Executor executor,
			List<AssociatedTaskExecutor> executors,ReactiveApiClient apiClient) {
		this.executor = executor;
		this.executorMap = executors.stream()
				.collect(Collectors.toMap(
						AssociatedTaskExecutor::getType,
						Function.identity()
				));
		this.apiClient=apiClient;
	}

	public void executeAssociatedTasks(List<CurrentProcess> processList, ApplicationDetails application,
			UserSessionObject user) {
		if (processList == null || processList.isEmpty()) {
			log.warn("No current processes found for application {}. Skipping associated task execution.",
                    application != null ? application.getApplicationId() : null);
			return;
		}
		 
		Integer serviceId = processList.get(0).getServiceId();
		log.info("Starting associated task execution. ApplicationId={}, ServiceId={}, TotalProcesses={}",
                application.getApplicationId(),
                serviceId,
                processList.size());
		apiClient.fetchServiceMetadata(user, serviceId, "ASSOCIATED_TASK").map(ServiceJSONDTO::getProcessFlowMap)
		.subscribe(workflow -> {

            int activityCount = workflow.getAssociatedActivities() == null
                    ? 0
                    : workflow.getAssociatedActivities().size();

            log.info("Associated task metadata loaded successfully. ServiceId={}, Activities={}",
                    serviceId,
                    activityCount);

            CompletableFuture.runAsync(() -> {

                log.info("Async execution started for ApplicationId={}",
                        application.getApplicationId());

                processList.forEach(process ->
                        executeForProcess(workflow, process, application, user,processList));

                log.info("Async execution completed for ApplicationId={}",
                        application.getApplicationId());

            }, executor);

        }, ex -> log.error(
                "Failed to fetch associated task metadata. ServiceId={}, ApplicationId={}",
                serviceId,
                application.getApplicationId(),
                ex));
	}

	private void executeForProcess(ServiceProcessFlowDTO workflow, CurrentProcess process,
			ApplicationDetails application, UserSessionObject user, List<CurrentProcess> processList) {
		log.info(
                "Checking associated tasks. ApplicationId={}, CurrentProcessId={}, CurrentTask={}, ActionTaken={}, ActionCode={}",
                application.getApplicationId(),
                process.getProcessId(),
                process.getCurrentTask(),
                process.getActionTaken(),
                process.getActionCode());
		
		List<ServiceProcessFlowDTO.AssociatedActivity> activities =
		        workflow.getAssociatedActivities()
		                .stream()
		                .filter(a -> activityMatched(a, process, processList))
		                .toList();

		if (activities.isEmpty()) {
			log.info(
                    "No associated activities matched. ApplicationId={}, CurrentTask={}",
                    application.getApplicationId(),
                    process.getCurrentTask());
			return;
		}
		log.info(
                "{} associated activity(s) matched for ApplicationId={}, CurrentTask={}",
                activities.size(),
                application.getApplicationId(),
                process.getCurrentTask());
		for (ServiceProcessFlowDTO.AssociatedActivity activity : activities) {

			try {
				log.info(
                        "Executing associated activity. ActivityId={}, Type={}, ApplicationId={}",
                        activity.getId(),
                        activity.getType(),
                        application.getApplicationId());
				AssociatedTaskExecutor executor = executorMap.get(activity.getType());

				if (executor == null) {
					log.info("No executor configured for associated task type {}", activity.getType());
					continue;
				}
				log.info(
                        "Associated activity executed successfully. ActivityId={}, Type={}, ApplicationId={}",
                        activity.getId(),
                        activity.getType(),
                        application.getApplicationId());
				executor.execute(activity, process, application, user);

			} catch (Exception ex) {
				log.error(
                        "Associated activity execution failed. ActivityId={}, Type={}, ApplicationId={}",
                        activity.getId(),
                        activity.getType(),
                        application.getApplicationId(),
                        ex);
			}
		}
	}

	private boolean activityMatched(ServiceProcessFlowDTO.AssociatedActivity activity, CurrentProcess process,
			List<CurrentProcess> processList) {

		if (activity.getTriggerPoint() == null || activity.getTriggerPoint().isEmpty()) {
			return false;
		}


		if (activity.getTriggerPoint().stream().anyMatch(tp -> "before".equalsIgnoreCase(tp))) {

			return Objects.equals(activity.getSourceTaskId(), process.getCurrentTask())
					&& "N".equalsIgnoreCase(process.getActionTaken());
		}

		if (activity.getTriggerPoint().stream()
				.anyMatch(tp -> "after".equalsIgnoreCase(tp))) {

			// Case 1 : No execution task configured
			// Execute immediately after source task completion.
			if (activity.getExecutionTasks() == null || activity.getExecutionTasks().isEmpty()) {
				
				if (!Objects.equals(activity.getSourceTaskId(), process.getCurrentTask())) {
					return false;
				}

				if (!"Y".equalsIgnoreCase(process.getActionTaken())) {
					return false;
				}

				if (activity.getTriggerOnAction() == null || activity.getTriggerOnAction().isEmpty()) {
					return true;
				}

				return activity.getTriggerOnAction().stream()
						.anyMatch(action -> Objects.equals(action, "action_" + process.getActionCode()));
			}

			// Case 2 : Execute on configured execution task
			if (!activity.getExecutionTasks().contains(process.getCurrentTask())) {
				return false;
			}

			CurrentProcess sourceProcess = processList.stream()
					.filter(p -> Objects.equals(p.getCurrentTask(), activity.getSourceTaskId()))
					.filter(p -> "Y".equalsIgnoreCase(p.getActionTaken())).findFirst().orElse(null);

			if (sourceProcess == null) {
				return false;
			}

			if (activity.getTriggerOnAction() == null || activity.getTriggerOnAction().isEmpty()) {
				return true;
			}

			return activity.getTriggerOnAction().stream()
					.anyMatch(action -> Objects.equals(action, "action_" + sourceProcess.getActionCode()));
		}

		return false;
	}


}
