package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.entity.TempTransactionLogs;
import reactor.core.publisher.Mono;

public interface RedisService {

    Mono<Object> fetch(String value,Class<?> obj);

    Mono<Boolean> add(Object ent, String key,boolean durationRequired);

    Mono<Void> remove(String key);
}
