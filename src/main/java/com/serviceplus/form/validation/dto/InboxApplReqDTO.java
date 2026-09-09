package com.serviceplus.form.validation.dto;

import java.util.List;

public class InboxApplReqDTO {

	private Long serviceId;
	private String taskId;
	private List<FilterDTO> filters;

	public static class FilterDTO {
		private String attrId;
		private String value;
		public String getAttrId() {
			return attrId;
		}
		public void setAttrId(String attrId) {
			this.attrId = attrId;
		}
		public String getValue() {
			return value;
		}
		public void setValue(String value) {
			this.value = value;
		}
	}

	public Long getServiceId() {
		return serviceId;
	}

	public void setServiceId(Long serviceId) {
		this.serviceId = serviceId;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public List<FilterDTO> getFilters() {
		return filters;
	}

	public void setFilters(List<FilterDTO> filters) {
		this.filters = filters;
	}

}
