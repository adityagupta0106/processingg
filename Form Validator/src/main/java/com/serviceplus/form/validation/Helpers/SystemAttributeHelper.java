package com.serviceplus.form.validation.Helpers;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.entity.ApplicationDetails;

@Component
public class SystemAttributeHelper {

	public Map<String,Object> systemAttrMap(Map<String, Object> systemAttrMap, ApplicationDetails applicationDetails,ServiceMeta service){
		if(applicationDetails!=null) {
			systemAttrMap.put("Sys_1", applicationDetails.getApplyDate());
			systemAttrMap.put("Sys_3", applicationDetails.getReferenceNo());
			systemAttrMap.put("Sys_1340", applicationDetails.getApplyDate());
			systemAttrMap.put("Sys_2025", applicationDetails.getServiceName());
			systemAttrMap.put("Sys_2040", applicationDetails.getApplicationId());
			systemAttrMap.put("Sys_2034", applicationDetails.getBeneficiaryName());
		}
		if(service!=null) {
			
		}

        System.out.println("syssssss " + systemAttrMap);
		return systemAttrMap;
		
	}
}
