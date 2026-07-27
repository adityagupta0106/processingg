package com.serviceplus.form.validation.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.serviceplus.form.validation.entity.WorkflowEscalation;
import com.serviceplus.form.validation.enums.EscalationStatus;
import com.serviceplus.form.validation.repository.EscalationRepository;

@Service
@Transactional
public class EscalationSchedulerServiceImpl implements EscalationSchedulerService {

	private static final Logger LOGGER = LogManager.getLogger("escalationSchedulerLogger");

	private final EscalationRepository escalationRepository;

	private final EscalationExecutionService escalationExecutionService;

	public EscalationSchedulerServiceImpl(EscalationRepository escalationRepository,
			EscalationExecutionService escalationExecutionService) {

		this.escalationRepository = escalationRepository;
		this.escalationExecutionService = escalationExecutionService;
	}

	@Override
	public void processPendingEscalations() {
		List<WorkflowEscalation> escalations = escalationRepository
				.findPendingEscalations(EscalationStatus.PENDING.name(), new Date());
		LOGGER.info("Pending Escalations : {}", escalations.size());
		for (WorkflowEscalation escalation : escalations) {
			try {
				escalationExecutionService.execute(escalation);
			} catch (Exception ex) {
				LOGGER.error("Escalation failed : {}", escalation.getId(), ex);
				escalation.setRetryCount(escalation.getRetryCount() == null ? 1 : escalation.getRetryCount() + 1);
				escalation.setStatus(EscalationStatus.FAILED.name());
				escalationRepository.save(escalation);
			}
		}
	}

}
