package com.serviceplus.form.validation.auaVerification.repository;

import com.serviceplus.form.validation.auaVerification.entity.AuaTransactionLog;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface AuaTransactionLogRepository extends ReactiveCrudRepository<AuaTransactionLog, Long> {

    Mono<AuaTransactionLog> findByTxnIdAndServiceIdAndTenantId(String txnId, Integer serviceId, String tenantId);

    Mono<AuaTransactionLog> findByTxnIdAndAttributeIdAndServiceIdAndTenantId(String txnId, String attributeId, Integer serviceId, String tenantId);

    Mono<AuaTransactionLog> findByTxnIdAndAttributeIdAndServiceIdAndTenantIdAndOperationType(String txnId, String attributeId, Integer serviceId, String tenantId, String operationType);
}
