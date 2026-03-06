package com.serviceplus.form.validation.utility;

import com.serviceplus.form.validation.handlers.ApplicationFlowHandler;
import com.serviceplus.form.validation.handlers.EnclosureHandler;
import com.serviceplus.form.validation.handlers.FormSubmissionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_ENCLOSURE_STATUS_KEY;
import static com.serviceplus.form.validation.utility.ApplicationConstants.ACTIVITY_FORM_STATUS_KEY;

@Component
public class HandlerMapping {

    public static Map<String, ApplicationFlowHandler> HANDLERS = new HashMap<>();

    @Autowired
    private FormSubmissionHandler formSubmissionHandler;

    @Autowired
    private EnclosureHandler enclosureHandler;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        HANDLERS.put(ACTIVITY_FORM_STATUS_KEY, formSubmissionHandler);
        HANDLERS.put(ACTIVITY_ENCLOSURE_STATUS_KEY, enclosureHandler);
       // HANDLERS.put("BEMVEL", enclosureHandler);
    }
}
