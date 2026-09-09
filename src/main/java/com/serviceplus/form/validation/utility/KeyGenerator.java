package com.serviceplus.form.validation.utility;

import java.security.SecureRandom;

public class KeyGenerator {

	 private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	    private static final int DEFAULT_KEY_LENGTH = 24;
	    private static final SecureRandom secureRandom = new SecureRandom();

	    public static String generatePassKey(int length) {
	        StringBuilder passKey = new StringBuilder(length);
	        for (int i = 0; i < length; i++) {
	            int randomIndex = secureRandom.nextInt(CHARACTERS.length());
	            passKey.append(CHARACTERS.charAt(randomIndex));
	        }
	        return passKey.toString();
	    }

	    public static String generatePassKey() {
	        return generatePassKey(DEFAULT_KEY_LENGTH);
	    }
}
