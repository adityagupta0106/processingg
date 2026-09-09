package com.serviceplus.form.validation.dto;

public class TimePeriod {

    private String duration;
    private LabelValue unit;

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public LabelValue getUnit() {
        return unit;
    }

    public void setUnit(LabelValue unit) {
        this.unit = unit;
    }
}
