package com.serviceplus.form.validation.dto;

import java.util.List;
import java.util.Set;

public class OfficeDetailsDTO {
	
	private String taskId;
	
	private Set<Integer> officeLevelIds;
	
	private List<OfficeUnitData> allowedOffices;
	
	public static class OfficeUnitData{
		
		private Integer orgUnitCode;
		
		private String orgUnitName;
		
		private List<String> holderIds;

		public Integer getOrgUnitCode() {
			return orgUnitCode;
		}

		public void setOrgUnitCode(Integer orgUnitCode) {
			this.orgUnitCode = orgUnitCode;
		}

		public String getOrgUnitName() {
			return orgUnitName;
		}

		public void setOrgUnitName(String orgUnitName) {
			this.orgUnitName = orgUnitName;
		}

		public List<String> getHolderIds() {
			return holderIds;
		}

		public void setHolderIds(List<String> holderIds) {
			this.holderIds = holderIds;
		}				
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public Set<Integer> getOfficeLevelIds() {
		return officeLevelIds;
	}

	public void setOfficeLevelIds(Set<Integer> officeLevelIds) {
		this.officeLevelIds = officeLevelIds;
	}

	public List<OfficeUnitData> getAllowedOffices() {
		return allowedOffices;
	}

	public void setAllowedOffices(List<OfficeUnitData> allowedOffices) {
		this.allowedOffices = allowedOffices;
	}
}
