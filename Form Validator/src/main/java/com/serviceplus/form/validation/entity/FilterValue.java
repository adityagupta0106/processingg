package com.serviceplus.form.validation.entity;

import java.util.List;

public class FilterValue {
    private Object value;
    private Operator operator;

    public FilterValue(Object value, Operator operator) {
        this.value = value;
        this.operator = operator;
    }

    public FilterValue() {
    }

    public enum Operator {
        EQUALS,
        IN,
        NOT_IN
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }
}
