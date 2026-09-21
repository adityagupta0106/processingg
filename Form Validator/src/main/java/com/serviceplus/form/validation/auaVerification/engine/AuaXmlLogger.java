package com.serviceplus.form.validation.auaVerification.engine;

import com.serviceplus.form.validation.auaVerification.dto.AuaApiConfigurationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.StringReader;
import java.io.StringWriter;

@Component
public class AuaXmlLogger {

    private static final Logger log = LoggerFactory.getLogger(AuaXmlLogger.class);

    public String prepareForLogging(String xml, AuaApiConfigurationDTO.AuaPayloadDTO payload) {

        if (xml == null || xml.isBlank()) {
            return xml;
        }

        if (payload == null || payload.getNodes() == null || payload.getNodes().isEmpty()) {
            return "[NO_LOGGING_CONFIGURATION]";
        }

        try {
            Document document = parseXml(xml);

            for (AuaApiConfigurationDTO.AuaNodeDTO node : payload.getNodes()) {

                if (node == null || node.getXpath() == null || node.getXpath().isBlank()) {
                    continue;
                }

                applyLoggingRule(document, node);
            }

            return toXml(document);

        } catch (Exception e) {

            log.warn("Unable to prepare AUA XML for logging. Returning sanitized placeholder.");

            return "[AUA_XML_LOGGING_FAILED]";
        }
    }

    private void applyLoggingRule(Document document, AuaApiConfigurationDTO.AuaNodeDTO node) {

        boolean logEnabled = node.isEnableLogs();
        boolean maskEnabled = node.isMaskValue();

        try {

            NodeList nodes = evaluateXPath(document, node.getXpath());

            if (nodes == null || nodes.getLength() == 0) {
                return;
            }

            for (int i = 0; i < nodes.getLength(); i++) {

                Node xmlNode = nodes.item(i);

                if (!logEnabled) {
                    replaceValue(xmlNode, "[NOT_LOGGED]");

                } else if (maskEnabled) {
                    replaceValue(xmlNode, maskValue(getNodeValue(xmlNode)));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void replaceValue(Node node, String value) {

        if (node == null) {
            return;
        }

        if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
            node.setNodeValue(value);
            return;
        }

        if (node.getNodeType() == Node.TEXT_NODE) {
            node.setNodeValue(value);
            return;
        }

        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {

            Node child = children.item(i);

            if (child.getNodeType() == Node.TEXT_NODE) {
                child.setNodeValue(value);
                return;
            }
        }

        node.setTextContent(value);
    }

    private String getNodeValue(Node node) {

        if (node == null) {
            return "";
        }

        if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
            return node.getNodeValue();
        }

        return node.getTextContent();
    }

    private String maskValue(String value) {

        if (value == null || value.isEmpty()) {
            return value;
        }

        int length = value.length();

        if (length <= 4) {
            return "*".repeat(length);
        }

        return "*".repeat(length - 4) + value.substring(length - 4);
    }

    private NodeList evaluateXPath(Document document, String xpathExpression) throws XPathExpressionException {

        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpath = xpathFactory.newXPath();

        XPathExpression expression = xpath.compile(xpathExpression);

        return (NodeList) expression.evaluate(document, XPathConstants.NODESET);
    }

    private Document parseXml(String xml) throws Exception {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);

        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);

        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();

        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private String toXml(Document document) throws Exception {

        TransformerFactory transformerFactory = TransformerFactory.newInstance();

        Transformer transformer = transformerFactory.newTransformer();

        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        StringWriter writer = new StringWriter();

        transformer.transform(new DOMSource(document), new StreamResult(writer));

        return writer.toString();
    }
}
