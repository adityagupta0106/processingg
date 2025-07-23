package com.serviceplus.form.validation.service;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;

@Service
public class FormManagementClient {

    @Autowired
    private ApiExecutor synchronousApiExecutor;

    @Value("${formmgmt.service}")
    private String FORM_MANAGEMENT_SERVICE;

    @SuppressWarnings("unchecked")
	public JSONObject getForm(String txnId, String formId) throws SPRuntimeError {
        String url = FORM_MANAGEMENT_SERVICE.concat("apply/form?txnId={txnId}&formId={formId}");

        ResponseEntity<String> response = (ResponseEntity<String>) synchronousApiExecutor.callExternalEndpoint(
                String.class, HttpMethod.GET, new HashMap<>(), Map.of("txnId", txnId, "formId", formId),
                url, "", MediaType.APPLICATION_JSON);

        try {
            return new JSONObject(response.getBody());
        } catch (JSONException je) {
            throw new SPRuntimeError("Unable to process your request", HttpStatus.FAILED_DEPENDENCY);
        }
    }
}

