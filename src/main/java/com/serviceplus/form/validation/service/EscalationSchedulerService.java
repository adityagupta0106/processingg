package com.serviceplus.form.validation.service;

import reactor.core.publisher.Mono;

public interface EscalationSchedulerService {

    Mono<Void> processPendingEscalations();

}
