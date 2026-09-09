package com.serviceplus.form.validation.dto;

public record FormPreparationContext(
        ServiceMeta service,
        String txnId,
        String workflowKey
) {
}
