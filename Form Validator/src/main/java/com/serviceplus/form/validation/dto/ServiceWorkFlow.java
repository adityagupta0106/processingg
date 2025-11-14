package com.serviceplus.form.validation.dto;

import java.util.List;

public class ServiceWorkFlow {

    private Integer serviceId;

    private List<Data> data;

    public static class Data{

        private Nodes node;
        private List<MappedTask> mappedTasks;

        public static class MappedTask{
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

            @Override
            public String toString() {
                return "Nodes{"
                        .concat("id='").concat(id != null ? id : "null").concat("'")
                        .concat(", type='").concat(type != null ? type : "null").concat("'")
                        .concat(", name='").concat(name != null ? name : "null").concat("'")
                        .concat(", behaviour='").concat(behaviour != null ? behaviour : "null").concat("'")
                        .concat("}");
            }


        }

        public List<MappedTask> getMappedTasks() {
            return mappedTasks;
        }

        public void setMappedTasks(List<MappedTask> mappedTasks) {
            this.mappedTasks = mappedTasks;
        }

        public Nodes getNode() {
            return node;
        }

        public void setNode(Nodes node) {
            this.node = node;
        }

        @Override
        public String toString() {
            return "Data{"
                    .concat("node=").concat(String.valueOf(node))
                    .concat(", mappedTasks=").concat(String.valueOf(mappedTasks))
                    .concat("}");
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
