package com.serviceplus.form.validation.dto;

public class RoutingSource {

    private final Long sourceLocationId;
    private final Integer sourceLevelCode;

    public RoutingSource(Long sourceLocationId, Integer sourceLevelCode) {
        this.sourceLocationId = sourceLocationId;
        this.sourceLevelCode = sourceLevelCode;
    }

    public Long getSourceLocationId() {
        return sourceLocationId;
    }

    public Integer getSourceLevelCode() {
        return sourceLevelCode;
    }
}
