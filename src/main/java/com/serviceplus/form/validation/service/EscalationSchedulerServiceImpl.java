package com.serviceplus.form.validation.service;

import java.time.LocalDateTime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.serviceplus.form.validation.enums.EscalationStatus;
import com.serviceplus.form.validation.repository.EscalationRepository;

import reactor.core.publisher.Mono;

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
	@Transactional
	public Mono<Void> processPendingEscalations() {

		return escalationRepository
				.findByStatusAndExecuteOnLessThanEqual(EscalationStatus.PENDING.name(), LocalDateTime.now())

				.doOnNext(escalation -> LOGGER.info("Processing escalation : {}", escalation.getId()))

				.flatMap(escalation -> escalationExecutionService.execute(escalation))

				.then();
	}
}