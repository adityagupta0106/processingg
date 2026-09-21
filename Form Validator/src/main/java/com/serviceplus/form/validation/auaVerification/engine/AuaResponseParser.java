package com.serviceplus.form.validation.auaVerification.engine;

import com.serviceplus.form.validation.auaVerification.dto.AuaApiConfigurationDTO;
import com.serviceplus.form.validation.auaVerification.dto.AuaResponse;
import com.serviceplus.form.validation.auaVerification.enums.AuaGenerationType;
import com.serviceplus.form.validation.auaVerification.enums.AuaMappingType;
import com.serviceplus.form.validation.auaVerification.enums.AuaMessageType;
import com.serviceplus.form.validation.auaVerification.enums.AuaResponseAttributeType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.w3c.dom.*;

import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

@Component
public class AuaResponseParser {

    private static final Logger log = LoggerFactory.getLogger(AuaResponseParser.class);

    public AuaResponse parse(AuaApiConfigurationDTO api, String xml) {

        if (api == null) {
            throw new IllegalArgumentException("AUA API configuration is required");
        }

        if (api.getResponsePayload() == null || api.getResponsePayload().getNodes() == null || api.getResponsePayload().getNodes().isEmpty()) {
            throw new IllegalArgumentException("AUA response payload is not configured");
        }

        log.info("Parsing AUA response. apiType={}", api.getApiType());

        AuaResponse result = new AuaResponse();

        Map<String, String> values = parseXmlAttributes(xml);

        log.info("Parsed AUA response attributes. attributeCount={}", values.size());

        for (AuaApiConfigurationDTO.AuaNodeDTO node : api.getResponsePayload().getNodes()) {

            if (node == null || node.getXpath() == null || node.getXpath().isBlank()) {
                continue;
            }

            String attributeName = extractAttributeName(node.getXpath());

            String value = values.get(attributeName);

            if (value == null) {
                log.info("AUA response attribute not found. nodeCode={}, xpath={}", node.getNodeCode(), node.getXpath());
                continue;
            }

            AuaMappingType mappingType = AuaMappingType.valueOf(node.getResponseAttributeType());

            switch (mappingType) {

                case RESULT:

                    boolean success = node.getDefaultValue() != null && node.getDefaultValue().equalsIgnoreCase(value);
                    result.setSuccess(success);
                    log.info("AUA RESULT processed. nodeCode={}, success={}", node.getNodeCode(), success);

                    break;

                case ERROR:

                    result.setErrorCode(value);
                    log.info("AUA ERROR processed. nodeCode={}, errorPresent={}", node.getNodeCode(), !value.isBlank());

                    break;

                case INFO:

                    String infoValue = extractInfoValue(value);

                    if (infoValue != null) {
                        result.setInfo(infoValue);
                    }

                    log.info("AUA INFO processed. nodeCode={}, sourcePath={}", node.getNodeCode(), node.getSourcePath());

                    break;

                default:

                    processResponseAttributeType(node, value, result);
                    break;
            }
        }

        log.info("AUA response parsing completed. apiType={}, success={}, errorCodePresent={}", api.getApiType(), result.isSuccess(), result.getErrorCode() != null && !result.getErrorCode().isBlank());

        return result;
    }

    private String extractInfoValue(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        int start = value.indexOf('{');

        if (start < 0 || start + 1 >= value.length()) {
            log.warn("Invalid AUA INFO response format.");
            return null;
        }

        String infoValue = value.substring(start + 1);

        if (infoValue.length() < 72) {
            log.warn("AUA INFO response is shorter than expected. length={}", infoValue.length());
            return null;
        }

        return infoValue.substring(0, 72);
    }

    private void processResponseAttributeType(AuaApiConfigurationDTO.AuaNodeDTO node, String value, AuaResponse result) {

        if (node.getResponseAttributeType() == null) {
            return;
        }

        if (AuaResponseAttributeType.RESULT.name().equalsIgnoreCase(node.getResponseAttributeType())) {

            boolean success = node.getDesiredResponse() != null && node.getDesiredResponse().equalsIgnoreCase(value);
            result.setSuccess(success);
            log.info("AUA dynamic RESULT processed. nodeCode={}, success={}", node.getNodeCode(), success);
        }

        if (AuaResponseAttributeType.ERROR.name().equalsIgnoreCase(node.getResponseAttributeType())) {
            result.setErrorCode(value);
            log.info("AUA dynamic ERROR processed. nodeCode={}, errorPresent={}", node.getNodeCode(), !value.isBlank());
        }
    }

    private String extractAttributeName(String xpath) {

        int index = xpath.lastIndexOf("@");

        if (index < 0) {
            throw new IllegalArgumentException("Invalid response XPath: " + xpath);
        }

        return xpath.substring(index + 1);
    }

    private Map<String, String> parseXmlAttributes(String xml) {

        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("AUA response XML is empty");
        }

        try {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);

            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();

            Document document = builder.parse(new InputSource(new StringReader(xml)));

            Map<String, String> values = new HashMap<>();

            Element root = document.getDocumentElement();

            if (root == null) {
                throw new IllegalArgumentException("Invalid AUA response XML");
            }

            collectAttributes(root, values);

            return values;

        } catch (Exception e) {

            log.error("Failed to parse AUA response XML", e);

            throw new IllegalArgumentException("Failed to parse AUA response XML", e);
        }
    }

    private void collectAttributes(Element element, Map<String, String> values) {

        NamedNodeMap attributes = element.getAttributes();

        for (int i = 0; i < attributes.getLength(); i++) {

            Node attribute = attributes.item(i);
            values.put(attribute.getNodeName(), attribute.getNodeValue());
        }

        NodeList children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {

            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectAttributes((Element) child, values);
            }
        }
    }
}