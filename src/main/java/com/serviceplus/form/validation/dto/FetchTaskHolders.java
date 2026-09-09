package com.serviceplus.form.validation.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FetchTaskHolders {

        private Integer serviceId;

        private Map<String, List<UserNode>> node = new LinkedHashMap<>();

        public static class UserNode {

            private String name;
            private String holderId;
            private Long locationId;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getHolderId() {
                return holderId;
            }

            public void setHolderId(String holderId) {
                this.holderId = holderId;
            }

            public Long getLocationId() {
                return locationId;
            }

            public void setLocationId(Long locationId) {
                this.locationId = locationId;
            }
        }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public Map<String, List<UserNode>> getNode() {
        return node;
    }

    public void setNode(Map<String, List<UserNode>> node) {
        this.node = node;
    }
}
