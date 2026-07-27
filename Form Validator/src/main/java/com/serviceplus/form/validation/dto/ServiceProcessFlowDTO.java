package com.serviceplus.form.validation.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.serviceplus.form.validation.dto.OfficeDetailsDTO.OfficeUnitData;
import com.serviceplus.form.validation.dto.WebServiceDetails.FormDetail;

public class ServiceProcessFlowDTO {
    private Integer serviceId;
    private List<Data> data;
    private List<AssociatedActivity> associatedActivities;
    private Map<String, TaskRelationDTO> taskRelation;
    
    public static class TaskRelationDTO {

	    private List<String> previousTask = new ArrayList<>();
	    private List<String> nextTask = new ArrayList<>();

	    public List<String> getPreviousTask() {
	        return previousTask;
	    }

	    public void setPreviousTask(List<String> previousTask) {
	        this.previousTask = previousTask;
	    }

	    public List<String> getNextTask() {
	        return nextTask;
	    }

	    public void setNextTask(List<String> nextTask) {
	        this.nextTask = nextTask;
	    }
	}
    public static class Data {

        private Nodes node;
        private List<MappedTask> mappedTasks;
        private List<OfficeUnitData> allowedOffices;
        private WorkflowElementData workflowElementData;

        public static class WorkflowElementData {

            private List<ActionAttribute> actionAttribute;

            private TaskAttribute taskAttribute;

            private UserAttribute userAttribute;

            public List<ActionAttribute> getActionAttribute() {
                return actionAttribute;
            }

            public void setActionAttribute(List<ActionAttribute> actionAttribute) {
                this.actionAttribute = actionAttribute;
            }

            public TaskAttribute getTaskAttribute() {
                return taskAttribute;
            }

            public void setTaskAttribute(TaskAttribute taskAttribute) {
                this.taskAttribute = taskAttribute;
            }

            public UserAttribute getUserAttribute() {
                return userAttribute;
            }

            public void setUserAttribute(UserAttribute userAttribute) {
                this.userAttribute = userAttribute;
            }
        }

        public static class ActionAttribute {

            private String key;
            private String label;
            private String trackLabel;
            private Boolean logicalClosure;
            private Boolean completeClosure;

            public String getKey() {
                return key;
            }

            public void setKey(String key) {
                this.key = key;
            }

            public String getLabel() {
                return label;
            }

            public void setLabel(String label) {
                this.label = label;
            }

            public String getTrackLabel() {
                return trackLabel;
            }

            public void setTrackLabel(String trackLabel) {
                this.trackLabel = trackLabel;
            }

            public Boolean getLogicalClosure() {
                return logicalClosure;
            }

            public void setLogicalClosure(Boolean logicalClosure) {
                this.logicalClosure = logicalClosure;
            }

            public Boolean getCompleteClosure() {
                return completeClosure;
            }

            public void setCompleteClosure(Boolean completeClosure) {
                this.completeClosure = completeClosure;
            }
        }

        public static class TaskAttribute {
            private String selectionType;
            private String gatewayType;
            private List<TaskNode> taskNodes;

            public String getSelectionType() {
                return selectionType;
            }

            public void setSelectionType(String selectionType) {
                this.selectionType = selectionType;
            }

            public List<TaskNode> getTaskNodes() {
                return taskNodes;
            }

            public void setTaskNodes(List<TaskNode> taskNodes) {
                this.taskNodes = taskNodes;
            }

            public String getGatewayType() {
                return gatewayType;
            }

            public void setGatewayType(String gatewayType) {
                this.gatewayType = gatewayType;
            }
        }

        public static class TaskNode {

            private String taskId;

            private String taskName;
            private Boolean showSelection;

            public String getTaskId() {
                return taskId;
            }

            public void setTaskId(String taskId) {
                this.taskId = taskId;
            }

            public String getTaskName() {
                return taskName;
            }

            public void setTaskName(String taskName) {
                this.taskName = taskName;
            }

            public Boolean getShowSelection() {
                return showSelection;
            }

            public void setShowSelection(Boolean showSelection) {
                this.showSelection = showSelection;
            }
        }

        public static class UserAttribute {

            private String selectionType;

            private List<UserNode> userNodes;

            public String getSelectionType() {
                return selectionType;
            }

            public void setSelectionType(String selectionType) {
                this.selectionType = selectionType;
            }

            public List<UserNode> getUserNodes() {
                return userNodes;
            }

            public void setUserNodes(List<UserNode> userNodes) {
                this.userNodes = userNodes;
            }
        }
        public static class UserNode {

            private String taskId;

            private Long locationId;

            private String locationName;

            private Boolean showSelection;

            private String holderId;

            private String holderName;

            public String getTaskId() {
                return taskId;
            }

            public void setTaskId(String taskId) {
                this.taskId = taskId;
            }

            public Long getLocationId() {
                return locationId;
            }

            public void setLocationId(Long locationId) {
                this.locationId = locationId;
            }

            public String getLocationName() {
                return locationName;
            }

            public void setLocationName(String locationName) {
                this.locationName = locationName;
            }

            public Boolean getShowSelection() {
                return showSelection;
            }

            public void setShowSelection(Boolean showSelection) {
                this.showSelection = showSelection;
            }

            public String getHolderId() {
                return holderId;
            }

            public void setHolderId(String holderId) {
                this.holderId = holderId;
            }

            public String getHolderName() {
                return holderName;
            }

            public void setHolderName(String holderName) {
                this.holderName = holderName;
            }
        }


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
            private Integer taskType;
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
            
        	public Integer getTaskType() {
				return taskType;
			}

			public void setTaskType(Integer taskType) {
				this.taskType = taskType;
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

        public WorkflowElementData getWorkflowElementData() {
            return workflowElementData;
        }

        public void setWorkflowElementData(WorkflowElementData workflowElementData) {
            this.workflowElementData = workflowElementData;
        }
    }

    public static class AssociatedActivity {

        private String id;

        private String type;

        private String sourceTaskId;

        private String edgeId;

        private List<String> triggerPoint;

	    private List<String> triggerOnAction;

	    private List<String> executionTasks;
	    
	    private FormDetail formDetail;

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

        public String getEdgeId() {
            return edgeId;
        }

        public void setEdgeId(String edgeId) {
            this.edgeId = edgeId;
        }

        public List<String> getTriggerPoint() {
			return triggerPoint;
		}

		public void setTriggerPoint(List<String> triggerPoint) {
			this.triggerPoint = triggerPoint;
		}
		

		public List<String> getTriggerOnAction() {
			return triggerOnAction;
		}

		public void setTriggerOnAction(List<String> triggerOnAction) {
			this.triggerOnAction = triggerOnAction;
		}

		public List<String> getExecutionTasks() {
			return executionTasks;
		}

		public void setExecutionTasks(List<String> executionTasks) {
			this.executionTasks = executionTasks;
		}
		
		public FormDetail getFormDetail() {
			return formDetail;
		}

		public void setFormDetail(FormDetail formDetail) {
			this.formDetail = formDetail;
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

	public Map<String, TaskRelationDTO> getTaskRelation() {
		return taskRelation;
	}

	public void setTaskRelation(Map<String, TaskRelationDTO> taskRelation) {
		this.taskRelation = taskRelation;
	}
    
}
