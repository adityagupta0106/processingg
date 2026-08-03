package com.serviceplus.form.validation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HandlerResponse {

    private Map<String,Object> response;

    private String txnId;
    private String applicationId;
    private String activityType;
    private List<ServiceMeta.AvailableApplyLocations> applyLocations;
    private boolean activityEnd;
    private ServiceProcessFlowDTO.Data.WorkflowElementData workflowElementData;
    private String workflowKey;

    public static class WorkflowElementData {

        private String gatewayType;

        private String selectionType;

        private List<ActionAttribute> actionAttribute;

        private List<TaskAttribute> taskAttribute;

        public String getGatewayType() {
            return gatewayType;
        }

        public void setGatewayType(String gatewayType) {
            this.gatewayType = gatewayType;
        }

        public String getSelectionType() {
            return selectionType;
        }

        public void setSelectionType(String selectionType) {
            this.selectionType = selectionType;
        }

        public List<ActionAttribute> getActionAttribute() {
            return actionAttribute;
        }

        public void setActionAttribute(List<ActionAttribute> actionAttribute) {
            this.actionAttribute = actionAttribute;
        }

        public List<TaskAttribute> getTaskAttribute() {
            return taskAttribute;
        }

        public void setTaskAttribute(List<TaskAttribute> taskAttribute) {
            this.taskAttribute = taskAttribute;
        }

        public static class ActionAttribute {

            private String key;

            private String label;

            public ActionAttribute() {
            }

            public ActionAttribute(String key, String label) {
                this.key = key;
                this.label = label;
            }

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
        }

        public static class TaskAttribute {

            private String taskId;

            private String taskName;

            public TaskAttribute() {
            }

            public TaskAttribute(String taskId, String taskName) {
                this.taskId = taskId;
                this.taskName = taskName;
            }

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
        }
    }

    public Map<String,Object> getData() {
        return response;
    }

    public void setData(Map<String,Object> data) {
        this.response = data;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public List<ServiceMeta.AvailableApplyLocations> getApplyLocations() {
        return applyLocations;
    }

    public void setApplyLocations(List<ServiceMeta.AvailableApplyLocations> applyLocations) {
        this.applyLocations = applyLocations;
    }

    public boolean isActivityEnd() {
        return activityEnd;
    }

    public void setActivityEnd(boolean activityEnd) {
        this.activityEnd = activityEnd;
    }

    public ServiceProcessFlowDTO.Data.WorkflowElementData getWorkflowElementData() {
        return workflowElementData;
    }

    public void setWorkflowElementData(ServiceProcessFlowDTO.Data.WorkflowElementData workflowElementData) {
        this.workflowElementData = workflowElementData;
    }

    public String getWorkflowKey() {
        return workflowKey;
    }

    public void setWorkflowKey(String workflowKey) {
        this.workflowKey = workflowKey;
    }
}
