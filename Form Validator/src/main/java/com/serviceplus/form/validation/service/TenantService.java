//package com.serviceplus.form.validation.service;
//
//import com.serviceplus.form.validation.executor.ApiExecutor;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//import org.springframework.stereotype.Service;
//import reactor.core.publisher.Mono;
//
//import java.util.HashMap;
//import java.util.Map;
//
//import static com.serviceplus.form.validation.utility.ApplicationConstants.HOST_HEADER;
//
//@Service
//public class TenantService {
//
//    @Autowired
//    private ApiExecutor apiExecutor;
//
//    @Value("${splus.instance.config.registry.service}")
//    private String INSTANCE_CONFIGURATION_REGISTRY_SERVICE;
//
//    public String fetchTenantId(ServerHttpRequest request) {
//
//        try {
//            String url = INSTANCE_CONFIGURATION_REGISTRY_SERVICE.concat("/b/fetchTenantId");
//            Map<String,String> headers = Map.of(HOST_HEADER,request.getHeaders().get(HOST_HEADER));
//
//            Mono<ResponseEntity<String>> callExternalEndpoint = apiExecutor.callExternalEndpoint(
//                                                                    String.class,
//                                                                    HttpMethod.POST,
//                                                                    headers,
//                                                                    Map.of("baseServiceId",baseServiceId,"taskId",taskId,"serviceId",serviceId),
//                                                                    url,
//                                                                    null,
//                                                                    MediaType.APPLICATION_JSON);
//
//            return (String) apiResponse.getBody();
//        } catch (Exception e) {
//            e.printStackTrace();
//            throw new SPRuntimeError("Something went wrong [TN-01]", HttpStatus.INTERNAL_SERVER_ERROR);
//        }
//    }
//}
