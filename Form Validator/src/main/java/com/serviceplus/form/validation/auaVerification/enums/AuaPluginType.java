package com.serviceplus.form.validation.auaVerification.enums;

public enum AuaPluginType {

    CREATE_SKEY,
    CREATE_DATA,
    CREATE_HMAC;

    public static AuaPluginType fromCode(String code) {

        if (code == null || code.isBlank()) {
            return null;
        }

        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported AUA plugin method: " + code);
        }
    }
}