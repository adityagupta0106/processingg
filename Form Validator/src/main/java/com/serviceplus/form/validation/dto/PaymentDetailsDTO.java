package com.serviceplus.form.validation.dto;

import java.util.List;

public class PaymentDetailsDTO {

    private String chargeId;

    private String sdutype;

    private List<String> roles;

    private List<PaymentGatewayDetailsDTO> paymentDetails;

    public PaymentDetailsDTO() {
    }

    public String getChargeId() {
        return chargeId;
    }

    public void setChargeId(String chargeId) {
        this.chargeId = chargeId;
    }

    public String getSdutype() {
        return sdutype;
    }

    public void setSdutype(String sdutype) {
        this.sdutype = sdutype;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<PaymentGatewayDetailsDTO> getPaymentDetails() {
        return paymentDetails;
    }

    public void setPaymentDetails(
            List<PaymentGatewayDetailsDTO> paymentDetails) {

        this.paymentDetails = paymentDetails;
    }
}