package com.serviceplus.form.validation.enums;

public enum DocumentMergeMode {

    NONE("none"),
    MERGE_ALL("mergeAll"),
    OVERWRITE("overwrite");

    private final String value;

    DocumentMergeMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DocumentMergeMode fromValue(String value) {

        if (value == null || value.isBlank()) {
            return NONE;
        }

        for (DocumentMergeMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }

        return NONE;
    }

}
