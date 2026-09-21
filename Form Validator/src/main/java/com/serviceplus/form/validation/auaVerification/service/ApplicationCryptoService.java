package com.serviceplus.form.validation.auaVerification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
public class ApplicationCryptoService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationCryptoService.class);

    @Value("classpath:keys/private_key.pem")
    private Resource privateKeyResource;

    @Value("classpath:keys/public_key.pem")
    private Resource publicKeyResource;

    public String encryptForTest(String plaintext) {

        try {

            PublicKey publicKey = loadPublicKey();

            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");

            cipher.init(Cipher.ENCRYPT_MODE, publicKey, getOaepParameterSpec());

            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(encrypted);

        } catch (Exception e) {

            log.error("Test RSA encryption failed", e);

            throw new IllegalStateException("Failed to encrypt value", e);
        }
    }


    public String decrypt(String encryptedValue) {

        try {

            if (encryptedValue == null || encryptedValue.isBlank()) {

                throw new IllegalArgumentException("Encrypted value cannot be empty");
            }

            log.info("Starting application attribute decryption");

            PrivateKey privateKey = loadPrivateKey();

            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedValue);

            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");

            cipher.init(Cipher.DECRYPT_MODE, privateKey, getOaepParameterSpec());

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            String plaintext = new String(decryptedBytes, StandardCharsets.UTF_8);

            log.info("Application attribute decrypted successfully");

            return plaintext;

        } catch (Exception e) {

            log.error("Failed to decrypt application attribute", e);

            throw new IllegalStateException("Failed to decrypt AUA attribute", e);
        }
    }

    private OAEPParameterSpec getOaepParameterSpec() {
        return new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    }


    private PrivateKey loadPrivateKey() {

        try {

            String pem;

            try (InputStream inputStream = privateKeyResource.getInputStream()) {

                pem = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            String key = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] decoded = Base64.getDecoder().decode(key);

            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            return keyFactory.generatePrivate(spec);

        } catch (Exception e) {

            throw new IllegalStateException("Failed to load private key", e);
        }
    }


    private PublicKey loadPublicKey() {

        try {

            String pem;

            try (InputStream inputStream = publicKeyResource.getInputStream()) {

                pem = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            String key = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] decoded = Base64.getDecoder().decode(key);

            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            return keyFactory.generatePublic(spec);

        } catch (Exception e) {

            throw new IllegalStateException("Failed to load public key", e);
        }
    }

    public void testEncryptionDecryption() {

        String original = "123456789012";

        log.info("Starting RSA encryption/decryption test");

        String encrypted = encryptForTest(original);

        log.info("RSA encryption successful. encryptedLength={},enc={}", encrypted.length(), encrypted);

        String decrypted = decrypt(encrypted);

        log.info("RSA decryption successful. valueMatches={}", original.equals(decrypted));

        if (!original.equals(decrypted)) {
            throw new IllegalStateException("RSA encryption/decryption test failed");
        }

        log.info("RSA encryption/decryption test PASSED");
    }
}