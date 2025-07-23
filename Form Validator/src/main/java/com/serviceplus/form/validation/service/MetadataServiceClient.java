package com.serviceplus.form.validation.service;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.google.gson.reflect.TypeToken;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.Services;
import com.serviceplus.form.validation.dto.UserSessionObject;
import static com.serviceplus.form.validation.utility.Utility.entityToString;
import static com.serviceplus.form.validation.utility.Utility.stringToEntityUsingType;

@Service
public class MetadataServiceClient {

    @Autowired
    private ApiExecutor synchronousApiExecutor;

    @Value("${metatdata.service}")
    private String METADATA_SERVICE;

    @SuppressWarnings("unchecked")
	public List<Services> fetchServiceList(UserSessionObject user) throws SPRuntimeError {
        Map<String, String> headers = Map.of("USER_SESSION_DETAIL_HEADER", entityToString(user));
        String url = METADATA_SERVICE.concat("apply/serviceList");

        ResponseEntity<String> response = (ResponseEntity<String>) synchronousApiExecutor.callExternalEndpoint(
                String.class, HttpMethod.GET, headers, new HashMap<>(), url, "", MediaType.APPLICATION_JSON);

        Type listType = new TypeToken<List<Services>>() {}.getType();
        List<Services> serviceList = (List<Services>) stringToEntityUsingType(response.getBody(), listType);

        return serviceList;
    }
}

