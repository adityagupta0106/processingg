package com.serviceplus.form.validation.auaVerification.dto;

import java.util.List;

public class AuaApiConfigurationDTO {

    private Long auaId;

    private String providerName;

    private Long apiId;

    private String apiCode;

    private String apiName;

    private String operationType;

    private String apiVersion;

    private String protocol;

    private String httpMethod;

    private String endpoint;

    private String description;

    private String xmlNamespace;

    private String xmlNamespaceVersion;

    public String getXmlNamespace() {
        return xmlNamespace;
    }

    public void setXmlNamespace(String xmlNamespace) {
        this.xmlNamespace = xmlNamespace;
    }

    public String getXmlNamespaceVersion() {
        return xmlNamespaceVersion;
    }

    public void setXmlNamespaceVersion(String xmlNamespaceVersion) {
        this.xmlNamespaceVersion = xmlNamespaceVersion;
    }

    private List<AuaApiMessageDTO> messages;

    public static class AuaApiMessageDTO {

        private Long messageId;

        private String messageType;

        private String rootElement;

        private List<AuaApiFieldDTO> fields;

        public static class AuaApiFieldDTO {

            private Long fieldId;

            private String fieldCode;

            private String fieldName;

            private String xpath;

            private String dataType;

            private String fieldType;

            private Boolean required;

            private Boolean multiple;

            private Boolean sensitive;

            private String sourceType;

            private String sourcePath;

            private String transformation;

            private String defaultValue;

            private Integer mappingOrder;

            private String responseAttributeType;

            private String desiredResponse;

            private Boolean display;

            public String getFieldName() {
                return fieldName;
            }

            public void setFieldName(String fieldName) {
                this.fieldName = fieldName;
            }

            public Long getFieldId() {
                return fieldId;
            }

            public void setFieldId(Long fieldId) {
                this.fieldId = fieldId;
            }

            public String getFieldCode() {
                return fieldCode;
            }

            public void setFieldCode(String fieldCode) {
                this.fieldCode = fieldCode;
            }

            public String getXpath() {
                return xpath;
            }

            public void setXpath(String xpath) {
                this.xpath = xpath;
            }

            public String getDataType() {
                return dataType;
            }

            public void setDataType(String dataType) {
                this.dataType = dataType;
            }

            public String getFieldType() {
                return fieldType;
            }

            public void setFieldType(String fieldType) {
                this.fieldType = fieldType;
            }

            public Boolean getRequired() {
                return required;
            }

            public void setRequired(Boolean required) {
                this.required = required;
            }

            public Boolean getMultiple() {
                return multiple;
            }

            public void setMultiple(Boolean multiple) {
                this.multiple = multiple;
            }

            public Boolean getSensitive() {
                return sensitive;
            }

            public void setSensitive(Boolean sensitive) {
                this.sensitive = sensitive;
            }

            public String getSourceType() {
                return sourceType;
            }

            public void setSourceType(String sourceType) {
                this.sourceType = sourceType;
            }

            public String getSourcePath() {
                return sourcePath;
            }

            public void setSourcePath(String sourcePath) {
                this.sourcePath = sourcePath;
            }

            public String getTransformation() {
                return transformation;
            }

            public void setTransformation(String transformation) {
                this.transformation = transformation;
            }

            public String getDefaultValue() {
                return defaultValue;
            }

            public void setDefaultValue(String defaultValue) {
                this.defaultValue = defaultValue;
            }

            public Integer getMappingOrder() {
                return mappingOrder;
            }

            public void setMappingOrder(Integer mappingOrder) {
                this.mappingOrder = mappingOrder;
            }

            public String getResponseAttributeType() {
                return responseAttributeType;
            }

            public void setResponseAttributeType(String responseAttributeType) {
                this.responseAttributeType = responseAttributeType;
            }

            public String getDesiredResponse() {
                return desiredResponse;
            }

            public void setDesiredResponse(String desiredResponse) {
                this.desiredResponse = desiredResponse;
            }

            public Boolean getDisplay() {
                return display;
            }

            public void setDisplay(Boolean display) {
                this.display = display;
            }
        }

        public List<AuaApiFieldDTO> getFields() {
            return fields;
        }

        public void setFields(List<AuaApiFieldDTO> fields) {
            this.fields = fields;
        }

        public String getRootElement() {
            return rootElement;
        }

        public void setRootElement(String rootElement) {
            this.rootElement = rootElement;
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public Long getMessageId() {
            return messageId;
        }

        public void setMessageId(Long messageId) {
            this.messageId = messageId;
        }
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public Long getAuaId() {
        return auaId;
    }

    public void setAuaId(Long auaId) {
        this.auaId = auaId;
    }

    public Long getApiId() {
        return apiId;
    }

    public void setApiId(Long apiId) {
        this.apiId = apiId;
    }

    public String getApiCode() {
        return apiCode;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public List<AuaApiMessageDTO> getMessages() {
        return messages;
    }

    public void setMessages(List<AuaApiMessageDTO> messages) {
        this.messages = messages;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
