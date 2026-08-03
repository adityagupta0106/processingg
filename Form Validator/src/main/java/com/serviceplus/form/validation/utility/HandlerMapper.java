package com.serviceplus.form.validation.utility;

import com.serviceplus.form.validation.handlers.ApplicationFlowHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HandlerMapper {

    private static final Map<String, ApplicationFlowHandler> HANDLERS = new HashMap<>();

    @Autowired
    public HandlerMapper(List<ApplicationFlowHandler> handlers) {
        handlers.forEach(h -> HANDLERS.put(h.getActivityType(), h));
    }

    public static ApplicationFlowHandler getHandler(String activityType) {
        return HANDLERS.get(activityType);
    }
}
