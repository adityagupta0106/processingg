package com.serviceplus.form.validation.dto;

public class MvelDetailsDTO {

    private String label;

    private String value;//trigger point

    private String nodeId;

    private Long mvelId;

    public MvelDetailsDTO() {
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public Long getMvelId() {
        return mvelId;
    }

    public void setMvelId(Long mvelId) {
        this.mvelId = mvelId;
    }
}

