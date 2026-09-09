package com.serviceplus.form.validation.dto;

import java.util.List;

public class WorkFlowDataDTO {
	
	private String taskId;
	
	private List<WorkFlowAction> allowedAction;

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public List<WorkFlowAction> getAllowedAction() {
		return allowedAction;
	}

	public void setAllowedAction(List<WorkFlowAction> allowedAction) {
		this.allowedAction = allowedAction;
	}
	
	public static class WorkFlowAction{
		
		private String actionKey;
		
		private String actionLabel;
		
		private String trackLabel;
		
		private String trackLabelOfficial;
		
		private Boolean remarksRequired;
		
		private Boolean logicalClosure;
		
		private Boolean completeClosure;

		public String getActionKey() {
			return actionKey;
		}

		public void setActionKey(String actionKey) {
			this.actionKey = actionKey;
		}

		public String getActionLabel() {
			return actionLabel;
		}

		public void setActionLabel(String actionLabel) {
			this.actionLabel = actionLabel;
		}

		public String getTrackLabel() {
			return trackLabel;
		}

		public void setTrackLabel(String trackLabel) {
			this.trackLabel = trackLabel;
		}

		public String getTrackLabelOfficial() {
			return trackLabelOfficial;
		}

		public void setTrackLabelOfficial(String trackLabelOfficial) {
			this.trackLabelOfficial = trackLabelOfficial;
		}

		public Boolean getRemarksRequired() {
			return remarksRequired;
		}

		public void setRemarksRequired(Boolean remarksRequired) {
			this.remarksRequired = remarksRequired;
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

}
