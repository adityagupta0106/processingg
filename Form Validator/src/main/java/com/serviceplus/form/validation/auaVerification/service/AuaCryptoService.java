package com.serviceplus.form.validation.auaVerification.service;

import com.serviceplus.form.validation.auaVerification.dto.AuaApiConfigurationDTO;
import com.serviceplus.form.validation.auaVerification.model.AuaCryptoPayload;
import com.serviceplus.form.validation.auaVerification.model.SessionKeyDetails;
import com.serviceplus.form.validation.auaVerification.model.SynchronizedKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class AuaCryptoService {

    private static final Logger log = LoggerFactory.getLogger(AuaCryptoService.class);

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * UIDAI public certificate.
     * <p>
     * application.properties:
     * <p>
     * uidai.publicKeyFile=classpath:keys/uidai_publicKeyFile.cer
     */
    @Value("${uidai.publicKeyFile}")
    private Resource publicKeyResource;


    /**
     * Generates the UIDAI cryptographic payload.
     * <p>
     * The XML passed to this method must be the PID XML
     * which has to be encrypted.
     * <p>
     * It must NOT already contain:
     *
     * <Signature>
     * <Skey>
     * <Data>
     * <Hmac>
     * <p>
     * Those values are generated here.
     */
    public AuaCryptoPayload generateCryptoPayload(String xml, AuaApiConfigurationDTO api) {

        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("PID XML cannot be empty");
        }

        if (api == null) {
            throw new IllegalArgumentException("AUA API configuration is required");
        }

        CertificateFile certificate = null;

        try {

            log.info("Starting AUA cryptographic processing. " + "apiId={}, apiCode={}, operationType={}", api.getApiId(), api.getApiCode(), api.getOperationType());


            // =========================================================
            // 1. Resolve UIDAI certificate
            // =========================================================

            certificate = resolveCertificateFile();

            File certificateFile = certificate.getFile();

            log.debug("UIDAI certificate resolved. apiId={}, path={}, temporary={}", api.getApiId(), certificateFile.getAbsolutePath(), certificate.isTemporary());


            // =========================================================
            // 2. Convert PID XML to UTF-8 bytes
            // =========================================================

            byte[] pid = xml.getBytes(StandardCharsets.UTF_8);

            log.debug("PID XML converted to bytes. apiId={}, size={}", api.getApiId(), pid.length);


            // =========================================================
            // 3. Create UIDAI encryption utility
            // =========================================================

            RDEncrypter encrypter = new RDEncrypter(certificateFile.getAbsolutePath());


            // =========================================================
            // 4. Generate random session key
            // =========================================================

            byte[] sessionKey = encrypter.generateSessionKey();

            log.debug("AUA session key generated. apiId={}", api.getApiId());


            // =========================================================
            // 5. Synchronize session key
            // =========================================================

            SynchronizedKey synchronizedKey = new SynchronizedKey(sessionKey, UUID.randomUUID().toString(), new Date());

            byte[] syncSessionKey = synchronizedKey.getSeedSkey();


            // =========================================================
            // 6. Encrypt session key using UIDAI public key
            // =========================================================
            //
            // The UIDAI public certificate is used here.
            //
            // UIDAI can therefore decrypt the session key
            // using the corresponding private key.
            //

            byte[] encryptedSessionKey = encrypter.encryptUsingPublicKey(syncSessionKey);


            // =========================================================
            // 7. Create SessionKeyDetails
            // =========================================================

            SessionKeyDetails sessionKeyDetails = SessionKeyDetails.createSkeyToInitializeSynchronizedKey(synchronizedKey.getKeyIdentifier(), encryptedSessionKey);


            // =========================================================
            // 8. Generate PID timestamp
            // =========================================================

            String timestamp = OffsetDateTime.now().format(TIMESTAMP_FORMATTER);

            log.debug("PID timestamp generated. apiId={}, timestamp={}", api.getApiId(), timestamp);


            // =========================================================
            // 9. Encrypt PID XML
            // =========================================================
            //
            // sessionKey is used to encrypt the PID XML.
            //

            byte[] cipherTextWithTS = encrypter.encrypt(pid, sessionKey, timestamp);


            // =========================================================
            // 10. Generate IV
            // =========================================================

            byte[] iv = encrypter.generateIv(timestamp);


            // =========================================================
            // 11. Generate AAD
            // =========================================================

            byte[] aad = encrypter.generateAad(timestamp);


            // =========================================================
            // 12. Generate SHA-256 hash of original PID
            // =========================================================

            byte[] srcHash = encrypter.generateHash(pid);


            // =========================================================
            // 13. Encrypt hash
            // =========================================================

            byte[] encryptedHash = encrypter.encryptDecryptUsingSessionKey(true, sessionKey, iv, aad, srcHash);


            // =========================================================
            // 14. Base64 encode encrypted PID
            // =========================================================

            String encryptedData = Base64.getEncoder().encodeToString(cipherTextWithTS);


            // =========================================================
            // 15. Base64 encode encrypted hash
            // =========================================================

            String encryptedHmac = Base64.getEncoder().encodeToString(encryptedHash);


            // =========================================================
            // 16. Base64 encode encrypted session key
            // =========================================================

            String encodedSessionKey = Base64.getEncoder().encodeToString(sessionKeyDetails.getSkeyValue());


            // =========================================================
            // 17. Get UIDAI certificate identifier
            // =========================================================

            String certificateIdentifier = encrypter.getCertificateIdentifier();


            log.info("AUA cryptographic processing completed. " + "apiId={}, certificateIdentifier={}, " + "encryptedDataPresent={}, encryptedHmacPresent={}", api.getApiId(), certificateIdentifier, encryptedData != null, encryptedHmac != null);


            // =========================================================
            // 18. Return cryptographic payload
            // =========================================================

            return new AuaCryptoPayload(certificateIdentifier, encodedSessionKey, encryptedData, encryptedHmac);

        } catch (Exception e) {

            log.error("AUA cryptographic processing failed. " + "apiId={}, apiCode={}", api.getApiId(), api.getApiCode(), e);

            throw new IllegalStateException("Failed to generate AUA cryptographic payload", e);

        } finally {

            if (certificate != null && certificate.isTemporary()) {

                try {

                    Files.deleteIfExists(certificate.getFile().toPath());
                    log.debug("Temporary UIDAI certificate deleted. path={}", certificate.getFile().getAbsolutePath());

                } catch (Exception e) {
                    log.warn("Unable to delete temporary UIDAI certificate. path={}", certificate.getFile().getAbsolutePath(), e);
                }
            }
        }
    }

    private CertificateFile resolveCertificateFile() {

        try {

            if (publicKeyResource == null) {
                throw new IllegalArgumentException("UIDAI public key resource is not configured");
            }


            log.info("UIDAI certificate resource = {}", publicKeyResource);

            log.info("UIDAI certificate exists = {}", publicKeyResource.exists());

            log.info("UIDAI certificate readable = {}", publicKeyResource.isReadable());

            log.info("UIDAI certificate filename = {}", publicKeyResource.getFilename());

            if (!publicKeyResource.exists()) {
                throw new IllegalArgumentException("UIDAI public key not found: " + publicKeyResource);
            }

            if (publicKeyResource.isFile()) {

                File file = publicKeyResource.getFile();
                log.info("Using existing UIDAI certificate file. path={}", file.getAbsolutePath());
                return new CertificateFile(file, false);
            }

            log.info("UIDAI certificate is not a physical file. " + "Creating temporary certificate file.");

            File tempFile = File.createTempFile("uidai-auth-", ".cer");

            try (InputStream inputStream = publicKeyResource.getInputStream()) {
                Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }


            log.info("UIDAI certificate copied to temporary file. path={}", tempFile.getAbsolutePath());

            return new CertificateFile(tempFile, true);

        } catch (Exception e) {
            throw new IllegalStateException("Unable to resolve UIDAI public certificate", e);
        }
    }


    private static class CertificateFile {

        private final File file;

        private final boolean temporary;


        public CertificateFile(File file, boolean temporary) {

            this.file = file;
            this.temporary = temporary;
        }


        public File getFile() {
            return file;
        }


        public boolean isTemporary() {
            return temporary;
        }
    }
}