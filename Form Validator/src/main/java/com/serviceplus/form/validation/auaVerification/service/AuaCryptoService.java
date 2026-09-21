package com.serviceplus.form.validation.auaVerification.service;

import com.serviceplus.form.validation.auaVerification.dto.AuaApiConfigurationDTO;
import com.serviceplus.form.validation.auaVerification.model.AuaCryptoContext;
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

    @Value("${uidai.publicKeyFile}")
    private Resource publicKeyResource;

    public String createSkey(AuaCryptoContext context, AuaApiConfigurationDTO api) {

        validateContext(context);
        validateApi(api);

        if (context.getEncryptedSessionKey() != null && !context.getEncryptedSessionKey().isBlank()) {

            log.info("CREATE_SKEY already executed. apiType={}", api.getApiType());
            return context.getEncryptedSessionKey();
        }

        CertificateFile certificate = null;

        try {

            log.info("Executing AUA CREATE_SKEY plugin. apiType={}", api.getApiType());

            certificate = resolveCertificateFile();
            File certificateFile = certificate.file();

            RDEncrypter encrypter = new RDEncrypter(certificateFile.getAbsolutePath());

            byte[] sessionKey = encrypter.generateSessionKey();
            context.setSessionKey(sessionKey);

            SynchronizedKey synchronizedKey = new SynchronizedKey(sessionKey, UUID.randomUUID().toString(), new Date());
            byte[] syncSessionKey = synchronizedKey.getSeedSkey();

            byte[] encryptedSessionKey = encrypter.encryptUsingPublicKey(syncSessionKey);

            SessionKeyDetails sessionKeyDetails = SessionKeyDetails.createSkeyToInitializeSynchronizedKey(synchronizedKey.getKeyIdentifier(), encryptedSessionKey);

            String encodedSessionKey = Base64.getEncoder().encodeToString(sessionKeyDetails.getSkeyValue());

            String certificateIdentifier = encrypter.getCertificateIdentifier();

            context.setCertificateIdentifier(certificateIdentifier);

            context.setEncryptedSessionKey(encodedSessionKey);

            log.info("CREATE_SKEY completed. apiType={}, certificateIdentifier={}", api.getApiType(), certificateIdentifier);

            return encodedSessionKey;

        } catch (Exception e) {

            log.error("CREATE_SKEY failed. apiType={}", api.getApiType(), e);

            throw new IllegalStateException("Failed to create AUA session key", e);

        } finally {

            deleteTemporaryCertificate(certificate);
        }
    }


    public String createData(AuaCryptoContext context, AuaApiConfigurationDTO api) {

        validateContext(context);
        validateApi(api);

        if (context.getPidXml() == null || context.getPidXml().isBlank()) {

            throw new IllegalArgumentException("PID XML cannot be empty");
        }

        if (context.getSessionKey() == null) {

            createSkey(context, api);
        }

        if (context.getEncryptedData() != null && !context.getEncryptedData().isBlank()) {

            return context.getEncryptedData();
        }

        CertificateFile certificate = null;

        try {

            log.info("Executing AUA CREATE_DATA plugin. apiType={}", api.getApiType());

            certificate = resolveCertificateFile();

            RDEncrypter encrypter = new RDEncrypter(certificate.file().getAbsolutePath());

            byte[] pid = context.getPidXml().getBytes(StandardCharsets.UTF_8);

            String timestamp = OffsetDateTime.now().format(TIMESTAMP_FORMATTER);

            byte[] cipherTextWithTS = encrypter.encrypt(pid, context.getSessionKey(), timestamp);

            String encryptedData = Base64.getEncoder().encodeToString(cipherTextWithTS);

            context.setEncryptedData(encryptedData);

            log.info("CREATE_DATA completed. apiType={}, encryptedDataLength={}", api.getApiType(), encryptedData.length());

            return encryptedData;

        } catch (Exception e) {

            log.error("CREATE_DATA failed. apiType={}", api.getApiType(), e);
            throw new IllegalStateException("Failed to create encrypted AUA data", e);

        } finally {
            deleteTemporaryCertificate(certificate);
        }
    }


    public String createHmac(AuaCryptoContext context, AuaApiConfigurationDTO api) {

        validateContext(context);
        validateApi(api);

        if (context.getPidXml() == null || context.getPidXml().isBlank()) {
            throw new IllegalArgumentException("PID XML cannot be empty");
        }

        if (context.getSessionKey() == null) {
            createSkey(context, api);
        }

        if (context.getEncryptedHmac() != null && !context.getEncryptedHmac().isBlank()) {
            return context.getEncryptedHmac();
        }

        CertificateFile certificate = null;

        try {

            log.info("Executing AUA CREATE_HMAC plugin. apiType={}", api.getApiType());

            certificate = resolveCertificateFile();

            RDEncrypter encrypter = new RDEncrypter(certificate.file().getAbsolutePath());

            byte[] pid = context.getPidXml().getBytes(StandardCharsets.UTF_8);

            String timestamp = OffsetDateTime.now().format(TIMESTAMP_FORMATTER);

            byte[] iv = encrypter.generateIv(timestamp);

            byte[] aad = encrypter.generateAad(timestamp);

            byte[] srcHash = encrypter.generateHash(pid);

            byte[] encryptedHash = encrypter.encryptDecryptUsingSessionKey(true, context.getSessionKey(), iv, aad, srcHash);
            String encryptedHmac = Base64.getEncoder().encodeToString(encryptedHash);
            context.setEncryptedHmac(encryptedHmac);

            log.info("CREATE_HMAC completed. apiType={}, encryptedHmacLength={}", api.getApiType(), encryptedHmac.length());

            return encryptedHmac;

        } catch (Exception e) {

            log.error("CREATE_HMAC failed. apiType={}", api.getApiType(), e);
            throw new IllegalStateException("Failed to create AUA HMAC", e);

        } finally {
            deleteTemporaryCertificate(certificate);
        }
    }

    private CertificateFile resolveCertificateFile() {

        try {

            if (publicKeyResource == null) {
                throw new IllegalArgumentException("UIDAI public key resource is not configured");
            }

            log.info("Resolving UIDAI public certificate. resource={}", publicKeyResource);

            if (!publicKeyResource.exists()) {
                throw new IllegalArgumentException("UIDAI public key not found: " + publicKeyResource);
            }

            if (publicKeyResource.isFile()) {

                File file = publicKeyResource.getFile();
                log.info("Using existing UIDAI certificate file. path={}", file.getAbsolutePath());
                return new CertificateFile(file, false);
            }

            log.info("UIDAI certificate is not a physical file. Creating temporary certificate.");

            File tempFile = File.createTempFile("uidai-auth-", ".cer");

            try (InputStream inputStream = publicKeyResource.getInputStream()) {
                Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Temporary UIDAI certificate created. path={}", tempFile.getAbsolutePath());

            return new CertificateFile(tempFile, true);

        } catch (Exception e) {

            log.error("Unable to resolve UIDAI public certificate", e);
            throw new IllegalStateException("Unable to resolve UIDAI public certificate", e);
        }
    }

    private void deleteTemporaryCertificate(CertificateFile certificate) {

        if (certificate == null || !certificate.temporary()) {
            return;
        }

        try {

            Files.deleteIfExists(certificate.file().toPath());
            log.info("Temporary UIDAI certificate deleted. path={}", certificate.file().getAbsolutePath());

        } catch (Exception e) {
            log.warn("Unable to delete temporary UIDAI certificate. path={}", certificate.file().getAbsolutePath(), e);
        }
    }

    private void validateContext(AuaCryptoContext context) {

        if (context == null) {
            throw new IllegalArgumentException("AUA crypto context is required");
        }
    }

    private void validateApi(AuaApiConfigurationDTO api) {

        if (api == null) {
            throw new IllegalArgumentException("AUA API configuration is required");
        }

        if (api.getApiType() == null || api.getApiType().isBlank()) {
            throw new IllegalArgumentException("AUA API type is required");
        }
    }

    public record CertificateFile(File file, boolean temporary) {

    }
}