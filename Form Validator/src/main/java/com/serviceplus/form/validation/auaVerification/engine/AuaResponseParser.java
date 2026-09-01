package com.serviceplus.form.validation.auaVerification.engine;

import com.serviceplus.form.validation.auaVerification.dto.AuaApiConfigurationDTO;
import com.serviceplus.form.validation.auaVerification.dto.AuaResponse;
import com.serviceplus.form.validation.auaVerification.enums.AuaGenerationType;
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

    private static final Logger log = LoggerFactory.getLogger("applicationFlowLogger");

    public AuaResponse parse(AuaApiConfigurationDTO api, String xml) {

        log.info("Parsing AUA response. apiId={}, apiCode={}, operationType={},xml={}",  api.getApiId(),  api.getApiCode() , api.getOperationType(),xml);

        AuaResponse result = new AuaResponse();

        AuaApiConfigurationDTO.AuaApiMessageDTO responseMessage = api.getMessages()
                .stream()
                .filter(message ->
                        AuaMessageType.RESPONSE.name().equalsIgnoreCase(message.getMessageType())
                ).findFirst()
                .orElseThrow(() -> {

                            log.error("RESPONSE message not configured. apiId={}, apiCode={}", api.getApiId(), api.getApiCode());
                            return new RuntimeException("RESPONSE message not configured");
                        });

        log.debug("AUA RESPONSE message found. messageId={}, rootElement={}", responseMessage.getMessageId(), responseMessage.getRootElement());

        Map<String, String> values = parseXmlAttributes(xml);
        log.debug("Parsed AUA response attributes. attributeCount={}", values.size());

        for (AuaApiConfigurationDTO.AuaApiMessageDTO.AuaApiFieldDTO field : responseMessage.getFields()) {

            String attributeType = field.getResponseAttributeType();

            if (attributeType == null) {
                continue;
            }


            String attributeName = extractAttributeName(field.getXpath());
            String value = values.get(attributeName);

            log.debug("Processing AUA response field. fieldId={}, fieldCode={}, attributeType={}, attributeName={}", field.getFieldId(), field.getFieldCode(), attributeType, attributeName);

            if (value == null) {

                log.warn("AUA response attribute not found. fieldId={}, fieldCode={}, xpath={}", field.getFieldId(), field.getFieldCode(), field.getXpath());
                continue;
            }



            if (AuaGenerationType.SYSTEM_INFO.name().equalsIgnoreCase(field.getTransformation())) {

                String processedInfo = processSystemInfo(value);

                result.setInfo(processedInfo);

                log.debug(
                        "AUA SYSTEM_INFO processed. fieldCode={}, originalLength={}, processedLength={}",
                        field.getFieldCode(),
                        value.length(),
                        processedInfo != null ? processedInfo.length() : 0
                );

                continue;
            }

            /*
             * RESULT
             */
            if (AuaResponseAttributeType.RESULT.name().equalsIgnoreCase(attributeType)) {

                boolean success = field.getDesiredResponse() != null && field.getDesiredResponse().equalsIgnoreCase(value);

                result.setSuccess(success);
                log.info("AUA RESULT processed. fieldCode={}, success={}", field.getFieldCode(), success);
            }

            /*
             * TRANSACTION ID
             */
            if (AuaResponseAttributeType.TRANSACTION_ID.name().equalsIgnoreCase(attributeType)) {

                result.setTransactionId(value);
                log.info("AUA transaction ID extracted. fieldCode={}", field.getFieldCode());
            }

            /*
             * ERROR
             */
            if (AuaResponseAttributeType.ERROR.name().equalsIgnoreCase(attributeType)) {

                result.setErrorCode(value);
                log.info("AUA error code extracted. fieldCode={}, hasError={}", field.getFieldCode(), !value.isBlank());
            }


        }

        log.info("AUA response parsing completed. apiId={}, success={}, transactionIdPresent={}, errorCodePresent={}", api.getApiId(), result.isSuccess(), result.getTransactionId() != null, result.getErrorCode() != null && !result.getErrorCode().isBlank());

        return result;
    }

    private String processSystemInfo(String value) {

        if (value == null || value.isBlank()) {
            return value;
        }

        if (value.startsWith("04{")) {
            return value.substring(3);
        }

        return value;
    }


    private String extractAttributeName(String xpath) {

        if (xpath == null || xpath.isBlank()) {

            log.error("AUA response field has empty XPath");
            throw new RuntimeException("Response XPath is empty");
        }

        int index = xpath.lastIndexOf("@");

        if (index < 0) {

            log.error("Invalid response attribute XPath: {}", xpath);
            throw new RuntimeException("Invalid response xpath: " + xpath);
        }

        return xpath.substring(index + 1);
    }


    private Map<String, String> parseXmlAttributes(String xml) {

        if (xml == null || xml.isBlank()) {

            log.error("AUA response XML is empty");
            throw new IllegalArgumentException("AUA response XML is empty");
        }

        try {

            log.debug("Starting secure XML parsing for AUA response");

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            /*
             * Prevent XXE.
             */
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

                log.error("AUA response XML does not contain a root element");
                throw new IllegalArgumentException("Invalid AUA response XML");
            }

            log.debug("AUA XML root element: {}", root.getNodeName());

            collectAttributes(root, values);

            log.debug("AUA XML attribute parsing completed. root={}, attributeCount={}", root.getNodeName(), values.size());

            return values;

        } catch (Exception e) {
            log.error("Failed to parse AUA response XML", e);
            throw new IllegalArgumentException("Failed to parse AUA response XML", e);
        }
    }


    private void collectAttributes(Element element, Map<String, String> values) {

        if (element == null) {
            return;
        }

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