package com.serviceplus.form.validation.dto;


public class DSCSignRequest {

    private String documentId;

    private Long userId;
    
    private String signedBase64;

    private String key;

    private String documentType;
    
    private String fileId;

    public String getDocumentId() {
		return documentId;
	}

	public void setDocumentId(String documentId) {
		this.documentId = documentId;
	}
	
	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getSignedBase64() {
        return signedBase64;
    }

    public void setSignedBase64(String signedBase64) {
        this.signedBase64 = signedBase64;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

	public String getFileId() {
		return fileId;
	}

	public void setFileId(String fileId) {
		this.fileId = fileId;
	}
    
}
