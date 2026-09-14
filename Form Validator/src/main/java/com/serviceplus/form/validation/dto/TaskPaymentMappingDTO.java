package com.serviceplus.form.validation.dto;

public class TaskPaymentMappingDTO {
	private Boolean enabled;
	private Long chargeDetailId;
	private Boolean systemGeneratedAck;
	private LabelValue paymentAckDoc;

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public Boolean getSystemGeneratedAck() {
		return systemGeneratedAck;
	}

	public void setSystemGeneratedAck(Boolean systemGeneratedAck) {
		this.systemGeneratedAck = systemGeneratedAck;
	}

	public Long getChargeDetailId() {
		return chargeDetailId;
	}

	public void setChargeDetailId(Long chargeDetailId) {
		this.chargeDetailId = chargeDetailId;
	}

	public LabelValue getPaymentAckDoc() {
		return paymentAckDoc;
	}

	public void setPaymentAckDoc(LabelValue paymentAckDoc) {
		this.paymentAckDoc = paymentAckDoc;
	}

}
