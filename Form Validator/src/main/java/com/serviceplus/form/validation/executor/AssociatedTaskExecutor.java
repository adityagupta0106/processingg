package com.serviceplus.form.validation.executor;

import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;

import reactor.core.publisher.Mono;

public interface AssociatedTaskExecutor {

	String getType();

	Mono<Void> execute(ServiceProcessFlowDTO.AssociatedActivity activity, CurrentProcess process,
			ApplicationDetails application, UserSessionObject user);
}
