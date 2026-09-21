package com.serviceplus.form.validation.auaVerification.engine;

import com.serviceplus.form.validation.auaVerification.dto.AuaApiConfigurationDTO;
import com.serviceplus.form.validation.auaVerification.entity.AuaTransactionLog;
import com.serviceplus.form.validation.auaVerification.enums.AadhaarSystemGeneratedType;
import com.serviceplus.form.validation.auaVerification.enums.AuaMappingType;
import com.serviceplus.form.validation.auaVerification.enums.AuaPluginType;
import com.serviceplus.form.validation.auaVerification.model.AuaCryptoContext;
import com.serviceplus.form.validation.auaVerification.service.AuaCryptoService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

@Component
public class AuaRequestBuilder {

    private static final Logger log = LoggerFactory.getLogger(AuaRequestBuilder.class);

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AuaCryptoService auaCryptoService;

    public AuaRequestBuilder(AuaCryptoService auaCryptoService) {
        this.auaCryptoService = auaCryptoService;
    }

    public String buildRequest(AuaApiConfigurationDTO api, Map<String, Object> attributes, AuaTransactionLog logEntry) {

        validateApi(api);

        log.info("Building AUA request. apiType={}", api.getApiType());

        AuaApiConfigurationDTO.AuaPayloadDTO requestPayload = api.getRequestPayload();

        if (requestPayload == null || requestPayload.getNodes() == null || requestPayload.getNodes().isEmpty()) {
            throw new IllegalArgumentException("AUA request payload is not configured");
        }

        RequestContext context = new RequestContext(attributes);

        XmlNode rootNode = buildUnsignedXml(requestPayload, api, context, logEntry);

        /*
         * Process plugin based crypto nodes.
         *
         * The PID XML is already present inside <Data>
         * at this point.
         */
        processPluginNodes(rootNode, requestPayload, api, context, logEntry);

        String finalXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + rootNode.toXml();

        log.info("AUA request generated successfully. apiType={}", api.getApiType());

        return finalXml;
    }

    private XmlNode buildUnsignedXml(AuaApiConfigurationDTO.AuaPayloadDTO requestPayload, AuaApiConfigurationDTO api, RequestContext context, AuaTransactionLog logEntry) {

        String rootElement = extractRootElement(requestPayload, api);

        String prefix = api.getPrefix();
        String suffix = api.getSuffix();

        XmlNode rootNode = new XmlNode(rootElement);

        if (api.getXmlNamespace() != null && !api.getXmlNamespace().isBlank()) {

            rootNode.setNamespace(api.getXmlNamespace());
        }

        for (AuaApiConfigurationDTO.AuaNodeDTO node : requestPayload.getNodes()) {

            if (node == null || node.getXpath() == null || node.getXpath().isBlank()) {

                continue;
            }

            AuaMappingType mappingType = node.getMappingType();

            /*
             * NONE
             */
            if (mappingType == null || mappingType == AuaMappingType.NONE) {

                log.info("Skipping AUA node. nodeCode={}, mappingType={}", node.getNodeCode(), mappingType);

                continue;
            }

            /*
             * PLUGIN nodes are processed separately
             * because some plugins depend on other
             * generated values.
             */
            if (mappingType == AuaMappingType.PLUGIN) {
                continue;
            }

            if (mappingType == AuaMappingType.RESULT || mappingType == AuaMappingType.ERROR) {
                continue;
            }

            String value = resolveValue(node, context, logEntry,prefix,suffix);

            if (value == null) {

                log.info("No value resolved for AUA node. nodeCode={}, mappingType={}", node.getNodeCode(), mappingType);

                continue;
            }

            addFieldToXml(rootNode, node, value);
        }

        return rootNode;
    }

    private String resolveValue(AuaApiConfigurationDTO.AuaNodeDTO node, RequestContext context, AuaTransactionLog logEntry, String prefix, String suffix) {

        AuaMappingType mappingType = node.getMappingType();

        if (mappingType == null) {
            return null;
        }

        if (mappingType == AuaMappingType.STATIC || mappingType == AuaMappingType.ADD_VALUE) {

            if (node.getDefaultValue() == null) {
                return null;
            }

            return node.getDefaultValue();
        }

        if (mappingType == AuaMappingType.DYNAMIC) {
            return resolveDynamicValue(node, context);
        }

        if (mappingType == AuaMappingType.SYSTEM_GENERATED) {
            return resolveSystemGeneratedValue(node, logEntry,prefix,suffix);
        }

        if (mappingType == AuaMappingType.PLUGIN) {
            return resolvePluginValue(node, context);
        }

        return null;
    }

    private String resolveDynamicValue(AuaApiConfigurationDTO.AuaNodeDTO node, RequestContext context) {

        if (node.getSourcePath() == null || node.getSourcePath().isBlank()) {
            return null;
        }

        Object value = getAttributeValue(node, context.getAttributes());

        if (value == null) {

            log.info("Dynamic value not found. nodeCode={}, sourcePath={}", node.getNodeCode(), node.getSourcePath());
            return null;
        }

        return String.valueOf(value);
    }

    private String resolveSystemGeneratedValue(AuaApiConfigurationDTO.AuaNodeDTO node, AuaTransactionLog logEntry, String prefix, String suffix) {

        String systemVariable = node.getSystemVariable();

        if (systemVariable == null || systemVariable.isBlank()) {
            return null;
        }

        AadhaarSystemGeneratedType type;

        try {
            type = AadhaarSystemGeneratedType.valueOf(systemVariable.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unsupported AUA system variable. nodeCode={}, systemVariable={}", node.getNodeCode(), systemVariable);
            return null;
        }

        switch (type) {

            case GENERATE_TXN:

                String txn = logEntry.getProviderTxnId();

                if (txn == null || txn.isBlank()) {
                    txn = generateTransactionId(prefix,suffix);
                    logEntry.setProviderTxnId(txn);
                }

                return txn;

            case CURRENT_TIMESTAMP:
                return generateTimestamp();

            case SIGN:
                return null;

            default:

                log.info("Unsupported AUA system variable. nodeCode={}, systemVariable={}", node.getNodeCode(), systemVariable);

                return null;
        }
    }

    private String resolvePluginValue(AuaApiConfigurationDTO.AuaNodeDTO node, RequestContext context) {

        String pluginMethodCode = node.getPluginMethodCode();

        if (pluginMethodCode == null || pluginMethodCode.isBlank()) {
            throw new IllegalArgumentException("Plugin method is not configured for node: " + node.getNodeCode());
        }

        AuaPluginType pluginType = AuaPluginType.fromCode(pluginMethodCode);

        if (pluginType == null) {
            throw new IllegalArgumentException("Invalid AUA plugin method: " + pluginMethodCode);
        }

        log.info("AUA plugin resolved. nodeCode={}, plugin={}", node.getNodeCode(), pluginType);
        return null;
    }

    private void processPluginNodes(XmlNode rootNode, AuaApiConfigurationDTO.AuaPayloadDTO requestPayload, AuaApiConfigurationDTO api, RequestContext context, AuaTransactionLog logEntry) {

        if (requestPayload == null || requestPayload.getNodes() == null || requestPayload.getNodes().isEmpty()) {

            return;
        }

        boolean hasSkeyPlugin = false;
        boolean hasDataPlugin = false;
        boolean hasHmacPlugin = false;

        for (AuaApiConfigurationDTO.AuaNodeDTO node : requestPayload.getNodes()) {

            if (node == null || node.getMappingType() != AuaMappingType.PLUGIN) {
                continue;
            }

            String pluginCode = node.getPluginMethodCode();

            if (pluginCode == null || pluginCode.isBlank()) {
                continue;
            }

            AuaPluginType pluginType = AuaPluginType.fromCode(pluginCode);

            if (pluginType == null) {
                continue;
            }

            switch (pluginType) {

                case CREATE_SKEY:
                    hasSkeyPlugin = true;
                    break;

                case CREATE_DATA:
                    hasDataPlugin = true;
                    break;

                case CREATE_HMAC:
                    hasHmacPlugin = true;
                    break;

                default:
                    break;
            }
        }

        if (!hasSkeyPlugin && !hasDataPlugin && !hasHmacPlugin) {
            return;
        }

        XmlNode dataNode = rootNode.children.get("Data");

        if (dataNode == null) {

            log.warn("AUA crypto plugins configured but Data node was not found. apiType={}", api.getApiType());
            return;
        }

        XmlNode pidNode = dataNode.children.get("Pid");

        if (pidNode == null) {

            log.warn("AUA crypto plugins configured but PID node was not found. apiType={}", api.getApiType());
            return;
        }

        String pidXml = pidNode.toXml();

        log.info("PID XML prepared for AUA plugin processing. apiType={}, size={}", api.getApiType(), pidXml.length());

        AuaCryptoContext cryptoContext = new AuaCryptoContext();

        cryptoContext.setPidXml(pidXml);


        if (hasSkeyPlugin) {

            String skey = auaCryptoService.createSkey(cryptoContext, api);
            log.info("CREATE_SKEY completed. apiType={}, generated={}", api.getApiType(), skey != null);
        }


        if (hasDataPlugin) {

            String encryptedData = auaCryptoService.createData(cryptoContext, api);
            cryptoContext.setEncryptedData(encryptedData);
            log.info("CREATE_DATA completed. apiType={}, generated={}", api.getApiType(), encryptedData != null);
        }

        if (hasHmacPlugin) {

            String encryptedHmac = auaCryptoService.createHmac(cryptoContext, api);
            cryptoContext.setEncryptedHmac(encryptedHmac);
            log.info("CREATE_HMAC completed. apiType={}, generated={}", api.getApiType(), encryptedHmac != null);
        }

        rootNode.children.remove("Data");

        addCryptoPayload(rootNode, cryptoContext);
    }

    private Object getAttributeValue(AuaApiConfigurationDTO.AuaNodeDTO node, Map<String, Object> attributes) {

        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        String sourcePath = node.getSourcePath();

        if (sourcePath == null || sourcePath.isBlank()) {
            return null;
        }

        Object value = attributes.get(sourcePath);

        if (value != null) {
            return value;
        }

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {

            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(sourcePath)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private void addFieldToXml(XmlNode rootNode, AuaApiConfigurationDTO.AuaNodeDTO node, String value) {

        String xpath = node.getXpath();

        if (xpath == null || xpath.isBlank()) {
            return;
        }

        String[] parts = xpath.split("/");

        XmlNode current = rootNode;

        int startIndex = 1;

        if (parts.length > 1 && rootNode.name.equals(parts[1])) {
            startIndex = 2;
        }

        for (int i = startIndex; i < parts.length; i++) {

            String part = parts[i];

            if (part == null || part.isBlank()) {
                continue;
            }


            if (part.startsWith("@")) {

                String attributeName = part.substring(1);
                current.attributes.put(attributeName, value);
                return;
            }

            current = current.children.computeIfAbsent(part, XmlNode::new);

            if (i == parts.length - 1) {

                current.text = value;
                return;
            }
        }
    }

    private void addCryptoPayload(XmlNode rootNode, AuaCryptoContext context) {

        if (context == null) {
            throw new IllegalArgumentException("AUA crypto context cannot be null");
        }

        StringBuilder xml = new StringBuilder();

        if (context.getEncryptedSessionKey() != null && !context.getEncryptedSessionKey().isBlank()) {

            xml.append("<Skey");

            if (context.getCertificateIdentifier() != null && !context.getCertificateIdentifier().isBlank()) {
                xml.append(" ci=\"").append(escapeXml(context.getCertificateIdentifier())).append("\"");
            }

            xml.append(">").append(escapeXml(context.getEncryptedSessionKey())).append("</Skey>");
        }


        if (context.getEncryptedData() != null && !context.getEncryptedData().isBlank()) {
            xml.append("<Data type=\"X\">").append(escapeXml(context.getEncryptedData())).append("</Data>");
        }

        if (context.getEncryptedHmac() != null && !context.getEncryptedHmac().isBlank()) {
            xml.append("<Hmac>").append(escapeXml(context.getEncryptedHmac())).append("</Hmac>");
        }

        rootNode.rawXml = xml.toString();
    }

    private void validateApi(AuaApiConfigurationDTO api) {

        if (api == null) {
            throw new IllegalArgumentException("AUA API configuration is required");
        }

        if (api.getRequestPayload() == null) {
            throw new IllegalArgumentException("AUA request payload is not configured");
        }
    }

    private String extractRootElement(AuaApiConfigurationDTO.AuaPayloadDTO payload, AuaApiConfigurationDTO api) {

        if (payload.getNodes() != null) {

            for (AuaApiConfigurationDTO.AuaNodeDTO node : payload.getNodes()) {

                if (node == null || node.getXpath() == null) {
                    continue;
                }

                String[] parts = node.getXpath().split("/");

                if (parts.length > 1 && !parts[1].isBlank()) {
                    return parts[1];
                }
            }
        }

        throw new IllegalArgumentException("Unable to determine AUA root element for apiType: " + api.getApiType());
    }

    private String generateTransactionId(String prefix, String suffix) {
        return generateTXN(prefix, suffix);
    }

    private String generateTimestamp() {
        return OffsetDateTime.now().format(TIMESTAMP_FORMATTER);
    }

    private String escapeXml(String value) {

        if (value == null) {
            return null;
        }

        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static class RequestContext {

        private final Map<String, Object> attributes;

        private final AuaCryptoContext cryptoContext;

        private RequestContext(Map<String, Object> attributes) {
            this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
            this.cryptoContext = new AuaCryptoContext();
        }

        private Map<String, Object> getAttributes() {
            return attributes;
        }

        private AuaCryptoContext getCryptoContext() {
            return cryptoContext;
        }
    }

    private class XmlNode {

        private final String name;

        private String text;

        private String rawXml;

        private String namespace;

        private final Map<String, String> attributes = new LinkedHashMap<>();

        private final Map<String, XmlNode> children = new LinkedHashMap<>();

        private XmlNode(String name) {
            this.name = name;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        private String toXml() {

            StringBuilder xml = new StringBuilder();

            xml.append("<").append(name);

            if (namespace != null && !namespace.isBlank()) {
                xml.append(" xmlns=\"").append(escapeXml(namespace)).append("\"");
            }

            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                xml.append(" ")
                        .append(attribute.getKey()).append("=\"")
                        .append(escapeXml(attribute.getValue())).append("\"");
            }

            if ((text == null || text.isEmpty()) && children.isEmpty() && (rawXml == null || rawXml.isEmpty())) {
                xml.append("/>");
                return xml.toString();
            }

            xml.append(">");

            if (text != null) {
                xml.append(escapeXml(text));
            }

            if (rawXml != null) {
                xml.append(rawXml);
            }

            for (XmlNode child : children.values()) {
                xml.append(child.toXml());
            }

            xml.append("</").append(name).append(">");
            return xml.toString();
        }
    }

    public String generateTXN(String prefix, String suffix) {

        Random random = new Random();

        long n = (long) (100000000000L + random.nextFloat() * 900000000000L);
        Date date = new Date();

        SimpleDateFormat ddForm = new SimpleDateFormat("dd");
        SimpleDateFormat mmForm = new SimpleDateFormat("MM");
        SimpleDateFormat yyyyForm = new SimpleDateFormat("yyyy");
        SimpleDateFormat hhForm = new SimpleDateFormat("hh");
        SimpleDateFormat minForm = new SimpleDateFormat("mm");
        SimpleDateFormat ssForm = new SimpleDateFormat("ss");
        String strDate = yyyyForm.format(date) + mmForm.format(date) + ddForm.format(date);
        String strTime = hhForm.format(date) + minForm.format(date) + ssForm.format(date);

        return prefix + n + strDate + strTime + suffix;
    }
}