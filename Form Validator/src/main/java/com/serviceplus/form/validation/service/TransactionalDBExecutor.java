package com.serviceplus.form.validation.service;

import org.springframework.data.domain.Sort;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

public interface TransactionalDBExecutor {

    Mono<Void> execute(String txnId,Object... entities);

    Mono<?> merge(Object entity);

    Mono<Long> executeRawSql(String sql, Map<String, Object> params,String txnId);

    Mono<?> fetchSingleEntity(
            String tenantId,
            Integer offset,
            Integer limit,
            LocalDateTime from,
            LocalDateTime to,
            Map<String,Object> params,
            String dateColumn,
            Class<?> clazz,
            Sort.Direction order,
            String orderBy
    );
}
