package com.serviceplus.form.validation.auaVerification.engine;

import com.serviceplus.form.validation.auaVerification.dto.AuaApiConfigurationDTO;
import com.serviceplus.form.validation.auaVerification.entity.AuaTransactionLog;
import com.serviceplus.form.validation.auaVerification.enums.AuaGenerationType;
import com.serviceplus.form.validation.auaVerification.enums.AuaMappingSourceType;
import com.serviceplus.form.validation.auaVerification.enums.AuaMessageType;
import com.serviceplus.form.validation.auaVerification.model.AuaCryptoPayload;
import com.serviceplus.form.validation.auaVerification.service.AuaCryptoService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.serviceplus.form.validation.utility.Utility.isEmpty;
import static java.util.Objects.isNull;


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

        log.info("Building AUA request. apiId={}, apiCode={}, operationType={}", api.getApiId(), api.getApiCode(), api.getOperationType());

        AuaApiConfigurationDTO.AuaApiMessageDTO requestMessage = getRequestMessage(api);

        RequestContext context = new RequestContext(attributes);

        /*
         * ---------------------------------------------------------
         * 1. Build normal Auth XML + internal PID XML
         * ---------------------------------------------------------
         */
        XmlNode rootNode = buildUnsignedXml(requestMessage, api, context, logEntry);
        System.out.println(rootNode.toXml());

        /*
         * ---------------------------------------------------------
         * 2. Extract PID XML.
         *
         * This is the ONLY XML that should be encrypted by
         * AuaCryptoService.
         * ---------------------------------------------------------
         */
        XmlNode dataNode = rootNode.children.get("Data");

        if (dataNode != null) {

            XmlNode pidNode = dataNode.children.get("Pid");

            if (pidNode != null) {

                String pidXml = pidNode.toXml();

                log.info(
                        "PID XML prepared for encryption. apiId={}, xml={}",
                        api.getApiId(),
                        pidXml
                );


                AuaCryptoPayload crypto = auaCryptoService.generateCryptoPayload(pidXml, api);

                rootNode.children.remove("Data");

                addCryptoPayload(rootNode, crypto);

                log.info(
                        "AUA cryptographic payload added. apiId={}, operationType={}",
                        api.getApiId(),
                        api.getOperationType()
                );

            } else {

                log.info(
                        "Data element exists but Pid element is not present. " +
                                "Skipping AUA encryption. apiId={}, operationType={}",
                        api.getApiId(),
                        api.getOperationType()
                );
            }

        } else {

            log.debug(
                    "AUA request does not contain Data/Pid. " +
                            "Skipping cryptographic processing. apiId={}, operationType={}",
                    api.getApiId(),
                    api.getOperationType()
            );
        }

        String finalXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + rootNode.toXml();

        log.info("AUA request generated successfully. apiId={}, apiCode={}", api.getApiId(), api.getApiCode());

        log.info("Final AUA XML. apiId={}, xml={}", api.getApiId(), finalXml);

        return finalXml;
    }


    private XmlNode buildUnsignedXml(AuaApiConfigurationDTO.AuaApiMessageDTO requestMessage, AuaApiConfigurationDTO api, RequestContext context, AuaTransactionLog logEntry) {

        XmlNode rootNode = new XmlNode(requestMessage.getRootElement());

        if (!isBlank(api.getXmlNamespace())) {

            rootNode.setNamespace(api.getXmlNamespace());

            log.info(
                    "XML namespace configured. apiId={}, rootElement={}, namespace={}, namespaceVersion={}",
                    api.getApiId(),
                    requestMessage.getRootElement(),
                    api.getXmlNamespace(),
                    api.getXmlNamespaceVersion()
            );
        }

        if (requestMessage.getFields() == null) {
            return rootNode;
        }


        for (AuaApiConfigurationDTO.AuaApiMessageDTO.AuaApiFieldDTO field : requestMessage.getFields()) {

            if (field == null) {
                continue;
            }

            if (isBlank(field.getXpath())) {
                continue;
            }


            if (isSignature(field)) {

                log.debug("Skipping signature field while building unsigned XML. fieldCode={}", field.getFieldCode());
                continue;
            }


            String value = resolveValue(field, context,logEntry);


            if (value == null) {

                if (Boolean.TRUE.equals(field.getRequired())) {
                    log.error("Required AUA field value missing. " + "fieldCode={}, sourceType={}, sourcePath={}, transformation={}", field.getFieldCode(), field.getSourceType(), field.getSourcePath(), field.getTransformation());
                    throw new IllegalArgumentException("Required AUA field value missing: " + field.getFieldCode());
                }

                continue;
            }


            addFieldToXml(rootNode, field, value);
        }

        return rootNode;
    }


    /*
     * =============================================================
     * RESOLVE FIELD VALUE
     * =============================================================
     */
    private String resolveValue(AuaApiConfigurationDTO.AuaApiMessageDTO.AuaApiFieldDTO field, RequestContext context, AuaTransactionLog logEntry) {

        String sourceType = normalize(field.getSourceType());

        String transformation = normalize(field.getTransformation());

        if (AuaMappingSourceType.CONSTANT.name().equalsIgnoreCase(sourceType)) {

            log.debug("Resolving CONSTANT field. fieldCode={}", field.getFieldCode());

            return field.getDefaultValue();
        }

        if (AuaMappingSourceType.SECRET.name().equalsIgnoreCase(sourceType)) {

            log.debug("Resolving SECRET field. fieldCode={}", field.getFieldCode());

            return resolveSecret(field.getSourcePath(), field.getDefaultValue());
        }


        if (AuaMappingSourceType.NONE.name().equalsIgnoreCase(sourceType)) {
            return null;
        }


        if (AuaMappingSourceType.DYNAMIC.name().equalsIgnoreCase(sourceType)) {

            if (AuaGenerationType.SYSTEM.name().equalsIgnoreCase(transformation)) {
                return "";
            }


            if (AuaGenerationType.GENERATE_TXN.name().equalsIgnoreCase(transformation)) {
                String txn = isNull(logEntry.getProviderTxnId()) ? generateTransactionId() : logEntry.getProviderTxnId();
                logEntry.setProviderTxnId(txn);
                log.debug("AUA transaction ID resolved. fieldCode={}", field.getFieldCode());
                return txn;
            }


            /*
             * -----------------------------------------------------
             * TIMESTAMP
             * -----------------------------------------------------
             */
            if (AuaGenerationType.CURRENT_TIMESTAMP.name().equalsIgnoreCase(transformation)) {

                String timestamp = context.getOrGenerate(field.getSourcePath(), this::generateTimestamp);
                log.debug("AUA timestamp resolved. fieldCode={}", field.getFieldCode());

                return timestamp;
            }


            /*
             * -----------------------------------------------------
             * SIGNATURE
             * -----------------------------------------------------
             *
             * This is deliberately NOT generated here.
             *
             * Signature is generated AFTER the complete unsigned
             * XML has been constructed.
             */
            if (AuaGenerationType.SIGN.name().equalsIgnoreCase(transformation)) {

                return null;
            }


            /*
             * -----------------------------------------------------
             * NORMAL DYNAMIC VALUE
             * -----------------------------------------------------
             */
            Object value = getAttributeValue(field, context.getAttributes());

            if (value == null) {

                log.debug("Dynamic value not found. fieldCode={}, sourcePath={}", field.getFieldCode(), field.getSourcePath());

                return null;
            }

            return String.valueOf(value);
        }


        return "";
        //throw new IllegalArgumentException("Unsupported AUA source type: " + field.getSourceType() + " for field: " + field.getFieldCode());
    }


    /*
     * =============================================================
     * FIND SIGNATURE FIELD
     * =============================================================
     */
    private AuaApiConfigurationDTO.AuaApiMessageDTO.AuaApiFieldDTO findSignatureField(AuaApiConfigurationDTO.AuaApiMessageDTO requestMessage) {

        if (requestMessage.getFields() == null) {
            return null;
        }

        return requestMessage.getFields().stream().filter(field -> field != null && isSignature(field)).findFirst().orElse(null);
    }


    private boolean isSignature(AuaApiConfigurationDTO.AuaApiMessageDTO.AuaApiFieldDTO field) {

        return AuaGenerationType.SIGN.name().equalsIgnoreCase(field.getTransformation());
    }

    private void addCryptoPayload(XmlNode rootNode, AuaCryptoPayload crypto) {

        if (crypto == null) {
            throw new IllegalArgumentException("AUA crypto payload cannot be null");
        }


        rootNode.rawXml = buildCryptoXml(crypto);

        log.info("Crypto payload added directly under Auth");
    }


    /*
     * =============================================================
     * BUILD CRYPTO XML
     * =============================================================
     */
    private String buildCryptoXml(AuaCryptoPayload crypto) {

        if (crypto == null) {
            throw new IllegalArgumentException("AUA crypto payload cannot be null");
        }


        StringBuilder xml = new StringBuilder();


        /*
         * Skey
         */
        xml.append("<Skey");

        if (!isBlank(crypto.getCertificateIdentifier())) {

            xml.append(" ci=\"").append(escapeXml(crypto.getCertificateIdentifier())).append("\"");
        }

        xml.append(">").append(escapeXml(crypto.getEncryptedSessionKey())).append("</Skey>");


        /*
         * Data
         */
        xml.append("<Data type=\"X\">").append(escapeXml(crypto.getEncryptedData())).append("</Data>");


        /*
         * Hmac
         */
        xml.append("<Hmac>").append(escapeXml(crypto.getEncryptedHmac())).append("</Hmac>");


        return xml.toString();
    }


    private void addFieldToXml(XmlNode rootNode, AuaApiConfigurationDTO.AuaApiMessageDTO.AuaApiFieldDTO field, String value) {

        String xpath = field.getXpath();

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

            /*
             * Attribute
             *
             * /Otp/@uid
             */
            if (part.startsWith("@")) {

                String attributeName = part.substring(1);

                current.attributes.put(attributeName, value);

                return;
            }

            /*
             * Element
             *
             * /Otp/Opts/@ch
             */
            current = current.children.computeIfAbsent(part, XmlNode::new);

            /*
             * If this is the final element,
             * put the value inside it.
             */
            if (i == parts.length - 1) {

                current.text = value;

                return;
            }
        }
    }


    /*
     * =============================================================
     * ATTRIBUTE VALUE
     * =============================================================
     */
    private Object getAttributeValue(AuaApiConfigurationDTO.AuaApiMessageDTO.AuaApiFieldDTO field, Map<String, Object> attributes) {

        if (attributes == null || attributes.isEmpty()) {

            return null;
        }

        String sourcePath = field.getSourcePath();

        if (isBlank(sourcePath)) {
            return null;
        }


        /*
         * Exact match.
         */
        Object value = attributes.get(sourcePath);

        if (value != null) {
            return value;
        }


        /*
         * Case-insensitive match.
         */
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {

            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(sourcePath)) {

                return entry.getValue();
            }
        }

        return null;
    }


    /*
     * =============================================================
     * API VALIDATION
     * =============================================================
     */
    private void validateApi(AuaApiConfigurationDTO api) {

        if (api == null) {

            throw new IllegalArgumentException("AUA API configuration is required");
        }

        if (api.getMessages() == null || api.getMessages().isEmpty()) {

            throw new IllegalArgumentException("AUA API messages are not configured");
        }
    }


    /*
     * =============================================================
     * REQUEST MESSAGE
     * =============================================================
     */
    private AuaApiConfigurationDTO.AuaApiMessageDTO getRequestMessage(AuaApiConfigurationDTO api) {

        return api.getMessages().stream().filter(message -> message != null && AuaMessageType.REQUEST.name().equalsIgnoreCase(message.getMessageType())).findFirst().orElseThrow(() -> new IllegalArgumentException("REQUEST message not configured for API: " + api.getApiCode()));
    }


    private String generateTransactionId() {
        return generateTXN(null,null);
    }


    private String generateTimestamp() {

        return OffsetDateTime.now().format(TIMESTAMP_FORMATTER);
    }


    private String resolveSecret(String sourcePath, String defaultValue) {
        return defaultValue;
    }

    private String normalize(String value) {

        if (value == null) {
            return null;
        }

        return value.trim().toUpperCase();
    }


    private boolean isBlank(String value) {
        return isEmpty(value);
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

        private final Map<String, String> generatedValues = new LinkedHashMap<>();


        private RequestContext(Map<String, Object> attributes) {

            this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
        }


        private Map<String, Object> getAttributes() {

            return attributes;
        }


        private String getOrGenerate(String key, java.util.function.Supplier<String> generator) {

            String actualKey = isBlankStatic(key) ? UUID.randomUUID().toString() : key;

            return generatedValues.computeIfAbsent(actualKey, k -> generator.get());
        }


        private static boolean isBlankStatic(String value) {

            return value == null || value.trim().isEmpty();
        }
    }

    private class XmlNode {

        private final String name;

        private String text;

        private String rawXml;

        private String namespace;

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getNamespace() {
            return namespace;
        }

        private final Map<String, String> attributes = new LinkedHashMap<>();

        private final Map<String, XmlNode> children = new LinkedHashMap<>();


        private XmlNode(String name) {
            this.name = name;
        }


        private String toXml() {

            StringBuilder xml = new StringBuilder();

            xml.append("<").append(name);

            if (namespace != null && !namespace.isBlank()) {
                xml.append(" xmlns=\"").append(escapeXml(namespace)).append("\"");
            }

            /*
             * Attributes.
             */
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {

                xml.append(" ").append(attribute.getKey()).append("=\"").append(escapeXml(attribute.getValue())).append("\"");
            }

            /*
             * Empty element.
             */
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

    public String generateTXN(String prefix,String suffix){
        if(prefix==null || prefix.equals("")){
            prefix = "NIC";
        }
        if(suffix==null || suffix.equals("")){
            suffix = "SPLS";
        }
        Random random = new Random();
        //long rand12Digit = random.nextLong();
        long n = (long) (100000000000L + random.nextFloat() * 900000000000L);

        Date date = new Date();
        SimpleDateFormat ddForm = new SimpleDateFormat("dd");
        SimpleDateFormat mmForm = new SimpleDateFormat("MM");
        SimpleDateFormat yyyyForm = new SimpleDateFormat("yyyy");
        SimpleDateFormat hhForm = new SimpleDateFormat("hh");
        SimpleDateFormat minForm = new SimpleDateFormat("mm");
        SimpleDateFormat ssForm = new SimpleDateFormat("ss");

        String strDD= ddForm.format(date);
        String strMM= mmForm.format(date);
        String strYYYYY= yyyyForm.format(date);
        String strHH= hhForm.format(date);
        String strMIN= minForm.format(date);
        String strSS= ssForm.format(date);

        String strDate = strYYYYY+strMM+strDD;
        String strTime = strHH+strMIN+strSS;

        String finalTXN = prefix + n + strDate + strTime + suffix;

        return finalTXN;
    }
}