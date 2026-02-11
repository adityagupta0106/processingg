package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Persistable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

@Service("ReactiveDBTransactionalExecutor")
public class ReactiveDBTransactionalExecutor implements TransactionalDBExecutor{

    @Autowired
    private TransactionalOperator transactionalOperator;

    @Autowired
    private R2dbcEntityTemplate entityTemplate;

    private static final Logger transactionLogger = LogManager.getLogger("transactionLogger");

    @Override
    public Mono<Void> execute(Object... entities) {

        return Flux.fromArray(entities)
                .concatMap(this::checkObject)
                .then()
                .as(transactionalOperator::transactional)

                .retryWhen(
                        Retry.backoff(1, Duration.ofMillis(50))
                        .maxBackoff(Duration.ofMillis(200)) //Not req here since max retry is set to 1 with delay of 50ms only for future updates
                        .filter(this::isRetryable)
                        .doBeforeRetry(r ->
                                transactionLogger.warn("Retrying transaction due to: {}", r.failure().toString())
                        )
                )

                .onErrorMap(ex -> {
                    transactionLogger.error("Transaction failed {} ,cause {}", ex,ex.getCause());
                    return new SPRuntimeError("Unable to save transaction", HttpStatus.INTERNAL_SERVER_ERROR);
                });
    }

    private Mono<Void> checkObject(Object obj) {
        if (obj == null) {
            return Mono.empty();
        }

        if (obj instanceof List<?> list) {
            return Flux.fromIterable(list)
                    .concatMap(this::merge)
                    .then();
        }

        return merge(obj);
    }

    @Override
    public Mono<Void> merge(Object entity) {
        if (entity == null) {
            return Mono.empty();
        }

        transactionLogger.debug("Merging record for class {}", entity.getClass());

        return entityTemplate.update(entity)
                .onErrorResume(org.springframework.dao.TransientDataAccessResourceException.class, ex -> {
                    String msg = ex.getMessage() != null ? ex.getMessage() : "";

                    if (msg.contains("does not exist")) {
                        transactionLogger.warn(
                                "Update failed because row does not exist -> inserting. Entity={}",
                                entity.getClass().getName()
                        );
                        return entityTemplate.insert(entity);
                    }

                    return Mono.error(ex);
                })
                .then();
    }

    private boolean isRetryable(Throwable ex) {

        transactionLogger.warn(
                "Retry check: exception={} message={}",
                ex.getClass().getName(),
                ex.getMessage()
        );

        boolean retryable = false;

        if (ex instanceof DuplicateKeyException) {
            transactionLogger.warn("Retryable detected: DuplicateKeyException (top-level)");
            retryable = true;
        } else if (ex instanceof DataIntegrityViolationException) {
            transactionLogger.warn("Retryable detected: DataIntegrityViolationException (top-level)");
            retryable = true;
        }

        Throwable cause = ex.getCause();
        int depth = 0;

        while (cause != null) {
            depth++;

            transactionLogger.warn(
                    "Retry check cause[{}]: exception={} message={}",
                    depth,
                    cause.getClass().getName(),
                    cause.getMessage()
            );

            if (cause instanceof DuplicateKeyException) {
                transactionLogger.warn("Retryable detected: DuplicateKeyException found in cause chain at depth {}", depth);
                retryable = true;
            } else if (cause instanceof DataIntegrityViolationException) {
                transactionLogger.warn("Retryable detected: DataIntegrityViolationException found in cause chain at depth {}", depth);
                retryable = true;
            }

            cause = cause.getCause();
        }

        if (retryable) {
            transactionLogger.warn("Retry decision: WILL RETRY");
        } else {
            transactionLogger.error("Retry decision: WILL NOT RETRY (non-retryable exception)");
        }

        return retryable;
    }

}
