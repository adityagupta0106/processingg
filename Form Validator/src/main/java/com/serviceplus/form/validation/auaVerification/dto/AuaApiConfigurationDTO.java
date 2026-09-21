package com.serviceplus.form.validation.auaVerification.dto;

import com.serviceplus.form.validation.auaVerification.enums.AuaMappingType;

import java.util.ArrayList;
import java.util.List;

public class AuaApiConfigurationDTO {

    private String apiType;
    private String endpoint;
    private String xmlNamespace;
    private String prefix;
    private String suffix;

    private AuaPayloadDTO requestPayload;
    private AuaPayloadDTO responsePayload;

    public static class AuaPayloadDTO {

        private List<AuaNodeDTO> nodes = new ArrayList<>();

        public List<AuaNodeDTO> getNodes() {
            return nodes;
        }

        public void setNodes(List<AuaNodeDTO> nodes) {
            this.nodes = nodes;
        }
    }

    public static class AuaNodeDTO {

        private String nodeCode;
        private String nodeName;
        private String xpath;

        /*
         * Master configuration
         */
        private AuaMappingType mappingType;

        /*
         * System generated configuration
         */
        private String systemVariable;

        /*
         * Plugin configuration
         */
        private String pluginMethodCode;

        /*
         * Service Definer request mapping
         */
        private String sourceType;
        private String sourcePath;
        private String transformation;
        private String defaultValue;

        /*
         * Service Definer response mapping
         */
        private String responseAttributeType;
        private String desiredResponse;
        private boolean enableLogs;

        private boolean maskValue;

        public String getNodeCode() {
            return nodeCode;
        }

        public void setNodeCode(String nodeCode) {
            this.nodeCode = nodeCode;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public String getXpath() {
            return xpath;
        }

        public void setXpath(String xpath) {
            this.xpath = xpath;
        }

        public AuaMappingType getMappingType() {
            return mappingType;
        }

        public void setMappingType(AuaMappingType mappingType) {
            this.mappingType = mappingType;
        }

        public String getSystemVariable() {
            return systemVariable;
        }

        public void setSystemVariable(String systemVariable) {
            this.systemVariable = systemVariable;
        }

        public String getPluginMethodCode() {
            return pluginMethodCode;
        }

        public void setPluginMethodCode(String pluginMethodCode) {
            this.pluginMethodCode = pluginMethodCode;
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

        public boolean isEnableLogs() {
            return enableLogs;
        }

        public void setEnableLogs(boolean enableLogs) {
            this.enableLogs = enableLogs;
        }

        public boolean isMaskValue() {
            return maskValue;
        }

        public void setMaskValue(boolean maskValue) {
            this.maskValue = maskValue;
        }
    }

    public String getApiType() {
        return apiType;
    }

    public void setApiType(String apiType) {
        this.apiType = apiType;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getXmlNamespace() {
        return xmlNamespace;
    }

    public void setXmlNamespace(String xmlNamespace) {
        this.xmlNamespace = xmlNamespace;
    }

    public AuaPayloadDTO getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(AuaPayloadDTO requestPayload) {
        this.requestPayload = requestPayload;
    }

    public AuaPayloadDTO getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(AuaPayloadDTO responsePayload) {
        this.responsePayload = responsePayload;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }
}