package com.serviceplus.form.validation.auaVerification.model;

import com.serviceplus.form.validation.auaVerification.service.RDEncrypter;

public class AuaCryptoContext {

    private String pidXml;
    private byte[] sessionKey;
    private String encryptedSessionKey;
    private String encryptedData;
    private String encryptedHmac;
    private String certificateIdentifier;
    private String timestamp;
    private RDEncrypter encrypter;

    public String getPidXml() {
        return pidXml;
    }

    public void setPidXml(String pidXml) {
        this.pidXml = pidXml;
    }

    public byte[] getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(byte[] sessionKey) {
        this.sessionKey = sessionKey;
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

    public String getCertificateIdentifier() {
        return certificateIdentifier;
    }

    public void setCertificateIdentifier(String certificateIdentifier) {
        this.certificateIdentifier = certificateIdentifier;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public RDEncrypter getEncrypter() {
        return encrypter;
    }

    public void setEncrypter(RDEncrypter encrypter) {
        this.encrypter = encrypter;
    }
}