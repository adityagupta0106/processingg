package com.serviceplus.form.validation.utility;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class AESUtil {

    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12;
    private static final int TAG_SIZE = 128;

    public static String encryptWithAESGCM(String data, SecretKey key) throws Exception {
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE, iv));

        byte[] encrypted = cipher.doFinal(data.getBytes());

        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
    }

    public static String decryptWithAESGCM(String cipherText, SecretKey key) throws Exception {
        byte[] decoded = Base64.getUrlDecoder().decode(cipherText);

        byte[] iv = Arrays.copyOfRange(decoded, 0, IV_SIZE);
        byte[] encrypted = Arrays.copyOfRange(decoded, IV_SIZE, decoded.length);

        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE, iv));

        return new String(cipher.doFinal(encrypted));
    }
}
