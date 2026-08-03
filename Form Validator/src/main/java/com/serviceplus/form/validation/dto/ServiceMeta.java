package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceMeta {

	private Integer serviceId;
	private String serviceName;
	@JsonIgnore
	private String formId;
	@JsonIgnore
	private String taskId;
    @JsonIgnore
    private String taskType;
	private String serviceKey;
    @JsonIgnore
    private Integer baseServiceId;
    @JsonIgnore
    private String currentProcessId;
    @JsonIgnore
    private List<AvailableApplyLocations> locations;
    private List<AvailableApplyLocations> nextLocations;
    //TODO nexTaskList from here

    @JsonIgnore
    private Long selectedLocationByUser;

    @JsonIgnore
    private String selectedLocationNameByUser;

    @JsonIgnore
    private ActivityMapDTO activityMap;

    private String departmentName;

    private ServiceProcessFlowDTO.Data.WorkflowElementData workflowElementData;

    private ServiceProcessFlowDTO.Data.WorkflowElementData selectedWorkflowElementData;

    private DocumentGenerationDetails documentGenerationDetails;

    private Object previousHandlerData;

    public DocumentGenerationDetails getDocumentGenerationDetails() {
        return documentGenerationDetails;
    }

    public void setDocumentGenerationDetails(DocumentGenerationDetails documentGenerationDetails) {
        this.documentGenerationDetails = documentGenerationDetails;
    }

    private Long issuedAt;

    private Long expiry;

    public ServiceProcessFlowDTO.Data.WorkflowElementData getWorkflowElementData() {
        return workflowElementData;
    }

    public void setWorkflowElementData(ServiceProcessFlowDTO.Data.WorkflowElementData workflowElementData) {
        this.workflowElementData = workflowElementData;
    }

    public static class AvailableApplyLocations{
        private Long orgUnitCode;
        private String orgUnitName;
        private List<String> holderIds;

        public Long getOrgUnitCode() {
			return orgUnitCode;
		}

		public void setOrgUnitCode(Long orgUnitCode) {
			this.orgUnitCode = orgUnitCode;
		}

		public String getOrgUnitName() {
			return orgUnitName;
		}

		public void setOrgUnitName(String orgUnitName) {
			this.orgUnitName = orgUnitName;
		}

		public void setLocationName(String locationName) {
            this.orgUnitName = locationName;
        }

        public List<String> getHolderIds() {
			return holderIds;
		}

		public void setHolderIds(List<String> holderIds) {
			this.holderIds = holderIds;
		}

		@Override
        public String toString() {
            return "AvailableApplyLocations{" +
                    "orgUnitCode=" + orgUnitCode +
                    ", orgUnitName='" + orgUnitName + '\'' +
                    '}';
        }
    }

	public Integer getServiceId() {
		return serviceId;
	}
	public void setServiceId(Integer serviceId) {
		this.serviceId = serviceId;
	}
	public String getServiceName() {
		return serviceName;
	}
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}
	public String getFormId() {
		return formId;
	}
	public void setFormId(String formId) {
		this.formId = formId;
	}
	public String getTaskId() {
		return taskId;
	}
	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}
	public String getServiceKey() {
		return serviceKey;
	}
	public void setServiceKey(String serviceKey) {
		this.serviceKey = serviceKey;
	}

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Integer getBaseServiceId() {
        return baseServiceId;
    }

    public void setBaseServiceId(Integer baseServiceId) {
        this.baseServiceId = baseServiceId;
    }

    public ActivityMapDTO getActivityMap() {
		return activityMap;
	}
	public void setActivityMap(ActivityMapDTO activityMap) {
		this.activityMap = activityMap;
	}
	public List<AvailableApplyLocations> getLocations() {
        return locations;
    }

    public void setLocations(List<AvailableApplyLocations> locations) {
        this.locations = locations;
    }

    public Long getSelectedLocationByUser() {
        return selectedLocationByUser;
    }

    public void setSelectedLocationByUser(Long selectedLocationByUser) {
        this.selectedLocationByUser = selectedLocationByUser;
    }

    public String getSelectedLocationNameByUser() {
        return selectedLocationNameByUser;
    }

    public void setSelectedLocationNameByUser(String selectedLocationNameByUser) {
        this.selectedLocationNameByUser = selectedLocationNameByUser;
    }

    public List<AvailableApplyLocations> getNextLocations() {
        return nextLocations;
    }

    public void setNextLocations(List<AvailableApplyLocations> nextLocations) {
        this.nextLocations = nextLocations;
    }

    @Override
    public String toString() {
        return "Services{"
                .concat("serviceId=").concat(String.valueOf(serviceId))
                .concat(", formId='").concat(formId).concat("'")
                .concat(", taskId='").concat(taskId).concat("'")
                .concat(", taskType='").concat(taskType).concat("'");
    }

    public String getCurrentProcessId() {
        return currentProcessId;
    }

    public void setCurrentProcessId(String currentProcessId) {
        this.currentProcessId = currentProcessId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public Long getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Long issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Long getExpiry() {
        return expiry;
    }

    public void setExpiry(Long expiry) {
        this.expiry = expiry;
    }

    public ServiceProcessFlowDTO.Data.WorkflowElementData getSelectedWorkflowElementData() {
        return selectedWorkflowElementData;
    }

    public void setSelectedWorkflowElementData(ServiceProcessFlowDTO.Data.WorkflowElementData selectedWorkflowElementData) {
        this.selectedWorkflowElementData = selectedWorkflowElementData;
    }

    public Object getPreviousHandlerData() {
        return previousHandlerData;
    }

    public void setPreviousHandlerData(Object previousHandlerData) {
        this.previousHandlerData = previousHandlerData;
    }
}
