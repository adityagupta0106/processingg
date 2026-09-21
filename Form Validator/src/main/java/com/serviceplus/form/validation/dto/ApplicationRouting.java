package com.serviceplus.form.validation.dto;

import java.util.List;

public class ApplicationRouting {

    private Boolean enabled;
    private String routingMode;
    private List<RoutingAttribute> selectedAttributes;
    private List<RoutingCombination> combinations;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getRoutingMode() {
        return routingMode;
    }

    public void setRoutingMode(String routingMode) {
        this.routingMode = routingMode;
    }

    public List<RoutingAttribute> getSelectedAttributes() {
        return selectedAttributes;
    }

    public void setSelectedAttributes(List<RoutingAttribute> selectedAttributes) {
        this.selectedAttributes = selectedAttributes;
    }

    public List<RoutingCombination> getCombinations() {
        return combinations;
    }

    public void setCombinations(List<RoutingCombination> combinations) {
        this.combinations = combinations;
    }


    // Nested class
    public static class RoutingAttribute {

        private String taskId;
        private String attributeType;
        private String attributeId;
        private String attributeKey;
        private String attributeName;
        private Integer priority;
        private Integer hierarchyLevel;

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getAttributeType() {
            return attributeType;
        }

        public void setAttributeType(String attributeType) {
            this.attributeType = attributeType;
        }

        public String getAttributeId() {
            return attributeId;
        }

        public void setAttributeId(String attributeId) {
            this.attributeId = attributeId;
        }

        public String getAttributeKey() {
            return attributeKey;
        }

        public void setAttributeKey(String attributeKey) {
            this.attributeKey = attributeKey;
        }

        public String getAttributeName() {
            return attributeName;
        }

        public void setAttributeName(String attributeName) {
            this.attributeName = attributeName;
        }

        public Integer getPriority() {
            return priority;
        }

        public void setPriority(Integer priority) {
            this.priority = priority;
        }

        public Integer getHierarchyLevel() {
            return hierarchyLevel;
        }

        public void setHierarchyLevel(Integer hierarchyLevel) {
            this.hierarchyLevel = hierarchyLevel;
        }
    }


    // Nested class
    public static class RoutingCombination {

    	private Integer priority;

	    private List<String> sourceAttributeIds;
	    
	    private List<String> destinationAttributeIds;

	    public Integer getPriority() {
	        return priority;
	    }

	    public void setPriority(Integer priority) {
	        this.priority = priority;
	    }

		public List<String> getSourceAttributeIds() {
			return sourceAttributeIds;
		}

		public void setSourceAttributeIds(List<String> sourceAttributeIds) {
			this.sourceAttributeIds = sourceAttributeIds;
		}

		public List<String> getDestinationAttributeIds() {
			return destinationAttributeIds;
		}

		public void setDestinationAttributeIds(List<String> destinationAttributeIds) {
			this.destinationAttributeIds = destinationAttributeIds;
		}
    }
}