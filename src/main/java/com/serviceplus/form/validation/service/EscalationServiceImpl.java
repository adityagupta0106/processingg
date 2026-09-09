package com.serviceplus.form.validation.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;

@Service
public class EscalationServiceImpl implements EscalationService{

	@Override
	public void scheduleEscalation(List<CurrentProcess> processList, ApplicationDetails application,
			UserSessionObject user) {
		
	}

}
