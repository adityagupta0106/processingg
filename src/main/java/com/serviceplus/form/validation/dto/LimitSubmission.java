package com.serviceplus.form.validation.dto;

import java.util.Map;

public class LimitSubmission {

	private Map<String, LimitData> data;
	private boolean behaviour;

	public Map<String, LimitData> getData() {
		return data;
	}

	public void setData(Map<String, LimitData> data) {
		this.data = data;
	}

	public boolean isBehaviour() {
		return behaviour;
	}

	public void setBehaviour(boolean behaviour) {
		this.behaviour = behaviour;
	}

	public static class LimitData {
		private String yearly;
		private String monthly;

		public String getYearly() {
			return yearly;
		}

		public void setYearly(String yearly) {
			this.yearly = yearly;
		}

		public String getMonthly() {
			return monthly;
		}

		public void setMonthly(String monthly) {
			this.monthly = monthly;
		}
	}
}
