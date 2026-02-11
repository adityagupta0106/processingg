package com.serviceplus.form.validation.service;

import reactor.core.publisher.Mono;

public interface TransactionalDBExecutor {

    Mono<Void> execute(Object... entities);

    Mono<?> merge(Object entity);
}
