package com.serviceplus.form.validation.dto;

import java.util.List;

public class ActivityMapDTO {
	
	private String taskId;
	
	private List<ActivityData> data;
	
	public static class ActivityData{
		private Integer index;
		
		private String activityName;
		
		private String activityType;
		
		private Boolean isLast;

        private Boolean userSubmissionRequired;
        
        private Long activityConfigId;

		public ActivityData(String activityType) {
			this.activityType=activityType;
		}

		public Integer getIndex() {
			return index;
		}

		public void setIndex(Integer index) {
			this.index = index;
		}

		public String getActivityName() {
			return activityName;
		}

		public void setActivityName(String activityName) {
			this.activityName = activityName;
		}

		public String getActivityType() {
			return activityType;
		}

		public void setActivityType(String activityType) {
			this.activityType = activityType;
		}

		public Boolean getLast() {
			return isLast;
		}

		public void setIsLast(Boolean isLast) {
			this.isLast = isLast;
		}

        public void setLast(Boolean last) {
            isLast = last;
        }

        public Boolean getUserSubmissionRequired() {
            return userSubmissionRequired;
        }

        public void setUserSubmissionRequired(Boolean userSubmissionRequired) {
            this.userSubmissionRequired = userSubmissionRequired;
        }

		public Long getActivityConfigId() {
			return activityConfigId;
		}

		public void setActivityConfigId(Long activityConfigId) {
			this.activityConfigId = activityConfigId;
		}
        
    }

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public List<ActivityData> getData() {
		return data;
	}

	public void setData(List<ActivityData> data) {
		this.data = data;
	}
}
