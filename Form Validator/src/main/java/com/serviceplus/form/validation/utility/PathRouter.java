package com.serviceplus.form.validation.utility;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.controller.PreProcessingController;
import com.serviceplus.form.validation.controller.HandlerController;

@Configuration
public class PathRouter {

	@Autowired
    private PreProcessingController preProcessingController;

    @Autowired
    private HandlerController handlerController;

	@Value("${service.context.path}")
	private String contextPath;
	
    /**
     * Defines the routes for handling HTTP requests.
     */
    @Bean
    RouterFunction<ServerResponse> httpRoutes() {
        return RouterFunctions
                .route(GET(contextPath + "/a/serviceList"), preProcessingController::getServiceList)
        		.andRoute(POST(contextPath + "/a/apply"), preProcessingController::apply)
        		//.andRoute(POST(contextPath + "/a/form/submission"), preProcessingController::submitApplication)
                .andRoute(POST(contextPath + "/a/handler/action"), handlerController::processHandler)
                .andRoute(POST(contextPath + "/a/apply/serviceKey"), preProcessingController::fetchServiceKey);
    }
    
    
//    @Bean
//    RouterFunction<ServerResponse> httpRoutes() {
//        return RouterFunctions
//                .route()
//                .GET(contextPath + "/a/serviceList",accept(APPLICATION_JSON), preProcessingController::getServiceList)
//        		.POST(contextPath + "/a/apply",accept(APPLICATION_JSON), preProcessingController::apply)
//        		.build();
//    }
}
