package com.serviceplus.form.validation.auaVerification.model;

public class AuaCryptoPayload {

    private String certificateIdentifier;

    private String encryptedSessionKey;

    private String encryptedData;

    private String encryptedHmac;

    public AuaCryptoPayload() {
    }

    public AuaCryptoPayload(String certificateIdentifier, String encryptedSessionKey, String encryptedData, String encryptedHmac) {

        this.certificateIdentifier = certificateIdentifier;
        this.encryptedSessionKey = encryptedSessionKey;
        this.encryptedData = encryptedData;
        this.encryptedHmac = encryptedHmac;
    }

    public String getCertificateIdentifier() {
        return certificateIdentifier;
    }

    public void setCertificateIdentifier(String certificateIdentifier) {
        this.certificateIdentifier = certificateIdentifier;
    }

    public String getEncryptedSessionKey() {
        return encryptedSessionKey;
    }

    public void setEncryptedSessionKey(String encryptedSessionKey) {
        this.encryptedSessionKey = encryptedSessionKey;
    }

    public String getEncryptedData() {
        return encryptedData;
    }

    public void setEncryptedData(String encryptedData) {
        this.encryptedData = encryptedData;
    }

    public String getEncryptedHmac() {
        return encryptedHmac;
    }

    public void setEncryptedHmac(String encryptedHmac) {
        this.encryptedHmac = encryptedHmac;
    }

}
