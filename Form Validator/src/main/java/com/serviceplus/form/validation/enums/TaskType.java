package com.serviceplus.form.validation.enums;

public enum TaskType {

    OFFICIAL_TASK(14),
    WEB_SERVICE_TASK(15),
    APPLICANT_TASK(16),
    GATEWAY(21),
    TIMER_TASK(31),
    DBT(36),
    DBT_BENEFICIARY_REG(37),
    DBT_PAYMENT(38),
    DBT_DSC(39);

    private final Integer type;

    TaskType(Integer type) {
        this.type = type;
    }

    public Integer getType() {
        return type;
    }

    public static TaskType fromType(Integer type) {
        for (TaskType taskType : values()) {
            if (taskType.type.equals(type)) {
                return taskType;
            }
        }
        return null;
    }

    public static String getName(Integer type) {
        TaskType taskType = fromType(type);
        return taskType != null ? taskType.name() : null;
    }
}

