package com.serviceplus.form.validation.utility;

import com.serviceplus.form.validation.auaVerification.controller.AuaController;
import com.serviceplus.form.validation.controller.ApplicationFetchController;
import com.serviceplus.form.validation.controller.DSCSignController;
import com.serviceplus.form.validation.controller.HandlerController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.serviceplus.form.validation.controller.PreProcessingController;

@Configuration
public class PathRouter {

    private final PreProcessingController preProcessingController;

    private final HandlerController handlerController;

    private final ApplicationFetchController applicationFetchController;

    private final DSCSignController dscSignController;

    private final AuaController auaController;

    public PathRouter(PreProcessingController preProcessingController, HandlerController handlerController, ApplicationFetchController applicationFetchController, DSCSignController dscSignController, AuaController auaController) {
        this.preProcessingController = preProcessingController;
        this.handlerController = handlerController;
        this.applicationFetchController = applicationFetchController;
        this.dscSignController = dscSignController;
        this.auaController = auaController;
    }

    @Value("${service.context.path}")
	private String contextPath;
	
    /**
     * Defines the routes for handling HTTP requests.
     */
    @Bean
    RouterFunction<ServerResponse> httpRoutes() {
        return RouterFunctions
                .route(POST(contextPath + "/a/serviceList"), preProcessingController::getServiceList)
        		.andRoute(POST(contextPath + "/a/form/render"), preProcessingController::render)
        		//.andRoute(POST(contextPath + "/a/form/submission"), preProcessingController::submitApplication)
                .andRoute(POST(contextPath + "/a/form/handler/action"), handlerController::processAction)
                .andRoute(POST(contextPath + "/a/form/handler/loadDraft"), handlerController::draft)
                .andRoute(POST(contextPath + "/a/apply/serviceKey"), preProcessingController::fetchServiceKey)
                .andRoute(POST(contextPath + "/a/form/edit"), handlerController::edit)
                .andRoute(POST(contextPath + "/a/app/draft/list"), applicationFetchController::fetchApplications)
                .andRoute(POST(contextPath + "/a/app/preload"), applicationFetchController::loadApplicationAndFetchServiceKey)
                .andRoute(POST(contextPath + "/a/workflow/inbox/list"), preProcessingController::getWFPInbox)
                .andRoute(POST(contextPath + "/a/workflow/inbox/filter/list"), preProcessingController::getWFPInboxFilterApplications)
        		.andRoute(POST(contextPath + "/a/inbox/applications"), preProcessingController::getPendingApplications)
                .andRoute(POST(contextPath + "/a/form/open"), handlerController::open)
        		.andRoute(POST(contextPath + "/a/dsc/sign"),dscSignController::sign)
                .andRoute(POST(contextPath + "/a/aua/request"), auaController::request)
                .andRoute(POST(contextPath + "/a/aua/validate"), auaController::validate)
                .andRoute(POST(contextPath + "/a/aua/getPublicKey"), auaController::getPublicKey);
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
