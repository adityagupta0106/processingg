package com.serviceplus.form.validation.dto;

public class Base64UploadRequest {

    private String base64;
    private String fileName;

    public String getBase64() {
        return base64;
    }

    public void setBase64(String base64) {
        this.base64 = base64;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
