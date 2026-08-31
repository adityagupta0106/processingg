package com.serviceplus.form.validation.dto;

public class MatchedOutputAttr {
    private String attrLabel;
    private String attrValue;

    public MatchedOutputAttr() {
		super();
	}

	public MatchedOutputAttr(String attrLabel, String attrValue) {
        this.attrLabel = attrLabel;
        this.attrValue = attrValue;
    }

	public String getAttrLabel() {
		return attrLabel;
	}

	public void setAttrLabel(String attrLabel) {
		this.attrLabel = attrLabel;
	}

	public String getAttrValue() {
		return attrValue;
	}

	public void setAttrValue(String attrValue) {
		this.attrValue = attrValue;
	}
}