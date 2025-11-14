package com.serviceplus.form.validation.dto;

import java.util.List;

public class ServiceWorkFlow {

    private Integer serviceId;

    private List<Data> data;

    public static class Data{

        private Nodes node;
        private List<MappedTask> mappedTasks;

        public static class MappedTask{
            private Nodes nodes;

            public Nodes getNodes() {
                return nodes;
            }

            public void setNodes(Nodes nodes) {
                this.nodes = nodes;
            }
        }


        public static class Nodes {
            private String id;
            private String type;
            private String name;
            private String behaviour;

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
        }

        public Nodes getNodes() {
            return node;
        }

        public void setNodes(Nodes nodes) {
            this.node = nodes;
        }

        public List<MappedTask> getMappedTasks() {
            return mappedTasks;
        }

        public void setMappedTasks(List<MappedTask> mappedTasks) {
            this.mappedTasks = mappedTasks;
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
}
