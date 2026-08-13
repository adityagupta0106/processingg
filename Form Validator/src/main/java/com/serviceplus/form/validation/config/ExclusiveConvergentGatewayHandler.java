package com.serviceplus.form.validation.config;

import org.springframework.stereotype.Component;

import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.TaskRelationDTO;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.handlers.ConvergentGatewayHandler;
import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_EXCLUSIVE_CONVERGENT;

import reactor.core.publisher.Mono;

@Component
public class ExclusiveConvergentGatewayHandler implements ConvergentGatewayHandler {

	@Override
	public boolean supports(String gatewayType) {
		return GATEWAY_BEHAVIOUR_EXCLUSIVE_CONVERGENT.equalsIgnoreCase(gatewayType);
	}

	@Override
	public Mono<Boolean> canProceed(CurrentProcess gatewayProcess,CurrentProcess currentActionProcess, TaskRelationDTO taskRelation, String applicationId,
			Integer serviceId, String tenantId) {

		return Mono.just(true);
	}
}
