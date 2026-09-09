package com.serviceplus.form.validation.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class CustomQueryRepository {

    @Autowired
    private DatabaseClient databaseClient;

    public Mono<Long> updateCompletionNative(
            String schemaName,
            String applicationId,
            String processId,
            String status,
            boolean completion) {

        String query = String.format("""
            UPDATE %s.application_flow_status
            SET completion = :completion
            WHERE application_id = :applicationId
              AND process_id = :processId
              AND status = :status
            """, schemaName);

        return databaseClient.sql(query)
                .bind("applicationId", applicationId)
                .bind("processId", processId)
                .bind("status", status)
                .bind("completion", completion)
                .fetch()
                .rowsUpdated();
    }
}
