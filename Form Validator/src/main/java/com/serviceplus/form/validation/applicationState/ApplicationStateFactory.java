package com.serviceplus.form.validation.applicationState;

import com.serviceplus.form.validation.CustomAnnotation.SanitizeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.serviceplus.form.validation.utility.ApplicationConstants.APPLICATION_STATUS_DRAFT;

@Service
@SanitizeRequest
public class ApplicationStateFactory {

    @Autowired
    private IncompleteApplication incompleteApplication;

    @Autowired
    private PipeLineApplication pipeLineApplication;

    public ApplicationManager getManager(String status){
        if (status.equals("draft")) {
            return incompleteApplication;
        }
        return pipeLineApplication;
    }
}
