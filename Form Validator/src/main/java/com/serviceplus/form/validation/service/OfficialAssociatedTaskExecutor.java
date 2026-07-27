package com.serviceplus.form.validation.service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.dto.OfficeDetailsDTO;
import com.serviceplus.form.validation.dto.OfficialIntimationKafkaDTO;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.dto.WorkflowAssignmentDTO;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.executor.AssociatedTaskExecutor;
import com.serviceplus.form.validation.kafka.KafkaProducer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class OfficialAssociatedTaskExecutor implements AssociatedTaskExecutor {

    private static final Logger log = LogManager.getLogger("associateTaskLogger");

    @Autowired
    private ReactiveApiClient apiClient;

    @Autowired
    private KafkaProducer kafkaProducer;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${push.official.intimation.message.topic}")
    private String PUSH_OFFICIAL_INTIMATION_MESSAGE_TOPIC;

    @Override
    public String getType() {
        return "official";
    }

	@Override
	public Mono<Void> execute(ServiceProcessFlowDTO.AssociatedActivity activity, CurrentProcess process,
			ApplicationDetails application, UserSessionObject user) {
		try {
			ServiceProcessFlowDTO.OfficialIntimation official = activity.getOfficialIntimation();
			if (official == null || official.getAllowedOffices() == null || official.getAllowedOffices().isEmpty()) {
				return Mono.empty();
			}
			log.info("Executing Official Intimation. applicationId={}, associatedTask={}",
					application.getApplicationId(), activity.getId());
			Flux.fromIterable(official.getAllowedOffices())
					.flatMap(office ->
					apiClient.fetchWorkflowAssignments(process.getServiceId(), activity.getId(), // Associated Task Id
							office.getOrgUnitCode().toString(), user, process.getProcessId())
							.map(assignments -> {
								List<String> holderIds = assignments.stream().map(WorkflowAssignmentDTO::getHolderId)
										.filter(Objects::nonNull).distinct().collect(Collectors.toList());
								office.setHolderIds(holderIds);
								return office;
							}))
					.collectList()
					.subscribe(offices -> {
						OfficialIntimationKafkaDTO dto = buildKafkaDTO(activity, process, application, user, offices);
						try {
							kafkaProducer.sendMessage(PUSH_OFFICIAL_INTIMATION_MESSAGE_TOPIC, dto.getApplicationId(),
									objectMapper.writeValueAsString(dto));
						} catch (JsonProcessingException e) {
							e.printStackTrace();
						}
						log.info("Official Intimation published. applicationId={}, associatedTask={}, offices={}",
								application.getApplicationId(), activity.getId(), offices.size());
					}, ex ->
					log.error("Official Intimation failed. associatedTask={}", activity.getId(), ex));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return Mono.empty();
	}

	private OfficialIntimationKafkaDTO buildKafkaDTO(ServiceProcessFlowDTO.AssociatedActivity activity,
			CurrentProcess process, ApplicationDetails application, UserSessionObject user,
			List<OfficeDetailsDTO.OfficeUnitData> offices) {

		OfficialIntimationKafkaDTO dto = new OfficialIntimationKafkaDTO();

		dto.setAssociatedTaskId(activity.getId());

		dto.setApplicationId(application.getApplicationId());
		dto.setApplicationRefNo(application.getReferenceNo());

		dto.setServiceId(process.getServiceId());
		dto.setBaseServiceId(process.getBaseServiceId());
		dto.setServiceName(process.getCurrentTaskName());

		dto.setCurrentProcessId(process.getProcessId());
		dto.setCurrentTaskId(process.getCurrentTask());
		dto.setCurrentTaskName(process.getCurrentTaskName());

		dto.setTenantId(process.getTenantId());

		dto.setAllowApplicationView(activity.getOfficialIntimation().getAllowApplicationView());

		dto.setAllowHistoryView(activity.getOfficialIntimation().getAllowHistoryView());

		dto.setAutoClear(activity.getOfficialIntimation().getAutoClear());

		dto.setAllowedOffices(offices);

		return dto;
	}
}