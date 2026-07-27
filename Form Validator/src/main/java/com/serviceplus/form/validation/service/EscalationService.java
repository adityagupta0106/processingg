package com.serviceplus.form.validation.service;

import java.util.List;

import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;

public interface EscalationService {

	void scheduleEscalation(List<CurrentProcess> processList, ApplicationDetails application, UserSessionObject user);

}
