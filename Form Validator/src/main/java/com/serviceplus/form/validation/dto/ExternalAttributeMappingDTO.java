package com.serviceplus.form.validation.dto;

import java.util.List;

public class ExternalAttributeMappingDTO {

	private Long externalSystemRegId;
	
	private String nodeId;
	
	private List<AttrMappingDetails> attrMappingDetails;

	public Long getExternalSystemRegId() {
		return externalSystemRegId;
	}

	public void setExternalSystemRegId(Long externalSystemRegId) {
		this.externalSystemRegId = externalSystemRegId;
	}
	
	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}
	
	public List<AttrMappingDetails> getAttrMappingDetails() {
		return attrMappingDetails;
	}

	public void setAttrMappingDetails(List<AttrMappingDetails> attrMappingDetails) {
		this.attrMappingDetails = attrMappingDetails;
	}
}
