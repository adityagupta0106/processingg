package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.entity.TempTransactionLogs;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;

public interface RedisService {

    Mono<Object> fetch(String value,Class<?> obj);

    <T> Mono<T> fetch(String key, Type type);

    Mono<Boolean> add(Object ent, String key,boolean durationRequired,final Integer minutes);

    Mono<Void> remove(String key);
}
