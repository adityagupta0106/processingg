package com.serviceplus.form.validation.handlers;

import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.TaskRelationDTO;
import com.serviceplus.form.validation.entity.CurrentProcess;

import reactor.core.publisher.Mono;

public interface ConvergentGatewayHandler {

	boolean supports(String gatewayType);

	Mono<Boolean> canProceed(CurrentProcess gatewayProcess, CurrentProcess currentActionProcess, TaskRelationDTO taskRelation, String applicationId,
			Integer serviceId, String tenantId);
}
