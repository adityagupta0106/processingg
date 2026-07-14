package com.serviceplus.form.validation.dto;

import java.util.List;

import com.serviceplus.form.validation.dto.OfficeDetailsDTO.OfficeUnitData;

public class ServiceProcessFlowDTO {
	private Integer serviceId;

	private List<Data> data;
	
	private List<AssociatedActivity> associatedActivities;

	public static class Data {

		private Nodes node;
		private List<MappedTask> mappedTasks;
		private List<OfficeUnitData> allowedOffices;

		public static class MappedTask {
			private Nodes node;

			public Nodes getNode() {
				return node;
			}

			public void setNode(Nodes node) {
				this.node = node;
			}

		}

		public static class Nodes {
			private String id;
			private String type;
			private String name;
			private String behaviour;
			private String formId;

			public String getId() {
				return id;
			}

			public void setId(String id) {
				this.id = id;
			}

			public String getType() {
				return type;
			}

			public void setType(String type) {
				this.type = type;
			}

			public String getName() {
				return name;
			}

			public void setName(String name) {
				this.name = name;
			}

			public String getBehaviour() {
				return behaviour;
			}

			public void setBehaviour(String behaviour) {
				this.behaviour = behaviour;
			}

			public String getFormId() {
				return formId;
			}

			public void setFormId(String formId) {
				this.formId = formId;
			}

		}

		public Nodes getNode() {
			return node;
		}

		public void setNode(Nodes node) {
			this.node = node;
		}

		public List<MappedTask> getMappedTasks() {
			return mappedTasks;
		}

		public void setMappedTasks(List<MappedTask> mappedTasks) {
			this.mappedTasks = mappedTasks;
		}

		public List<OfficeUnitData> getAllowedOffices() {
			return allowedOffices;
		}

		public void setAllowedOffices(List<OfficeUnitData> allowedOffices) {
			this.allowedOffices = allowedOffices;
		}
	}
	
	public static class AssociatedActivity {

	    private String id;

	    private String type;
	    
	    private String sourceTaskId;

	    private String targetTaskId;

	    private String edgeId;

	    private String triggerPoint;

	    private OfficialIntimation officialIntimation;

	    private WebServiceDetails webServiceDetails;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getSourceTaskId() {
			return sourceTaskId;
		}

		public void setSourceTaskId(String sourceTaskId) {
			this.sourceTaskId = sourceTaskId;
		}

		public String getTargetTaskId() {
			return targetTaskId;
		}

		public void setTargetTaskId(String targetTaskId) {
			this.targetTaskId = targetTaskId;
		}

		public String getEdgeId() {
			return edgeId;
		}

		public void setEdgeId(String edgeId) {
			this.edgeId = edgeId;
		}

		public String getTriggerPoint() {
			return triggerPoint;
		}

		public void setTriggerPoint(String triggerPoint) {
			this.triggerPoint = triggerPoint;
		}

		public OfficialIntimation getOfficialIntimation() {
			return officialIntimation;
		}

		public void setOfficialIntimation(OfficialIntimation officialIntimation) {
			this.officialIntimation = officialIntimation;
		}

		public WebServiceDetails getWebServiceDetails() {
			return webServiceDetails;
		}

		public void setWebServiceDetails(WebServiceDetails webServiceDetails) {
			this.webServiceDetails = webServiceDetails;
		}
	}
	
	public static class OfficialIntimation {

	    private List<OfficeUnitData> allowedOffices;

	    private Boolean allowApplicationView;

	    private Boolean allowHistoryView;

	    private Boolean autoClear;

		public List<OfficeUnitData> getAllowedOffices() {
			return allowedOffices;
		}

		public void setAllowedOffices(List<OfficeUnitData> allowedOffices) {
			this.allowedOffices = allowedOffices;
		}

		public Boolean getAllowApplicationView() {
			return allowApplicationView;
		}

		public void setAllowApplicationView(Boolean allowApplicationView) {
			this.allowApplicationView = allowApplicationView;
		}

		public Boolean getAllowHistoryView() {
			return allowHistoryView;
		}

		public void setAllowHistoryView(Boolean allowHistoryView) {
			this.allowHistoryView = allowHistoryView;
		}

		public Boolean getAutoClear() {
			return autoClear;
		}

		public void setAutoClear(Boolean autoClear) {
			this.autoClear = autoClear;
		}
	}

	public Integer getServiceId() {
		return serviceId;
	}

	public void setServiceId(Integer serviceId) {
		this.serviceId = serviceId;
	}

	public List<Data> getData() {
		return data;
	}

	public void setData(List<Data> data) {
		this.data = data;
	}

	public List<AssociatedActivity> getAssociatedActivities() {
		return associatedActivities;
	}

	public void setAssociatedActivities(List<AssociatedActivity> associatedActivities) {
		this.associatedActivities = associatedActivities;
	}
}
