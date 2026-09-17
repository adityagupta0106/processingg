package com.serviceplus.form.validation.dto;

public class Base64FileResponse {

    private String fileName;

    private String mimeType;

    private String base64;

    public Base64FileResponse(){

    }

    public Base64FileResponse(String fileName, String mimeType, String base64) {
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.base64 = base64;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getBase64() {
        return base64;
    }

    public void setBase64(String base64) {
        this.base64 = base64;
    }
}
