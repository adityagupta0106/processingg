package com.serviceplus.form.validation.utility;


import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;

import jakarta.annotation.PostConstruct;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import static com.serviceplus.form.validation.utility.ApplicationConstants.APPLY_METADATA_ENC_KEY;
import static com.serviceplus.form.validation.utility.CryptoUtil.HMACSHA256;
import static com.serviceplus.form.validation.utility.KeyGenerator.generatePassKey;

@Component
public class Utility {

	@Value("${aesAuthKey}")
	private String aesAuthKey;
	
	private static String AES_AUTH_KEY;

    private static final Logger applicationFlowLogs = LogManager.getLogger("applicationFlowLogger");

	@PostConstruct
	public void initalize() {
		AES_AUTH_KEY = this.aesAuthKey;
	}
	
	/**
	 * Fetch logged in user details
	 * @param request
	 * @return UserSessionObject
	 */
	public static UserSessionObject getUserSessionDetails(ServerHttpRequest request) {
		HttpHeaders headers = request.getHeaders();
		List<String> header = headers.get("USER-DETAILS");
		if(header == null || header.isEmpty())
			return null;
			
		return (UserSessionObject) stringToEntity(header.get(0),UserSessionObject.class);
	}
	
	public static Object stringToEntity(String data,Class<?> classs) {
		return new Gson().fromJson(data, classs);
	}
	
	public static String entityToString(Object data) {
		return new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter()).create().toJson(data);
	}
	
	public static Object stringToEntityUsingType(String data, Type typeOfT) {
	    return new Gson().fromJson(data, typeOfT);
	}
	
	public static boolean isEmpty(String s) {
		return s == null || s.trim().isEmpty();
	}
	
	public static String extractServiceName(String url) {
        if (url != null && url.startsWith("lb://")) {
            int firstSlash = url.indexOf('/', 5);
            if (firstSlash != -1) {
                return url.substring(5, firstSlash);
            }
            return url.substring(5);
        }
        return "unknown-service";
    }
	
	public static String SHA256(String plaintext) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-256");
			md.update(plaintext.getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			md = null;
		}

		StringBuffer ls_sb = new StringBuffer();

		if (md != null) {
			byte raw[] = md.digest();
			for (int i = 0; i < raw.length; i++)
				ls_sb.append(char2hex(raw[i]));
		}

		return ls_sb.toString();
	}

	public static String char2hex(byte x)

	{
		char arr[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

		char c[] = { arr[(x & 0xF0) >> 4], arr[x & 0x0F] };
		return (new String(c));
	}
	
	public static String AESEncrypt(String content,String key) {
		try {
			
			key = key == null ? AES_AUTH_KEY : key;
			
			SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
			cipher.init(Cipher.ENCRYPT_MODE, skeySpec,new IvParameterSpec(new byte[16]));
			byte[] encrypted = cipher.doFinal(content.getBytes("UTF-8"));
			String finalString = org.apache.commons.codec.binary.Base64.encodeBase64String(encrypted);
			
			return finalString;
		} catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static String AESDecrypt(String content,String key) {
		try {
			
			key = key == null ? AES_AUTH_KEY : key;
			
			SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
			cipher.init(Cipher.DECRYPT_MODE, skeySpec,new IvParameterSpec(new byte[16]));
			byte[] original = cipher.doFinal(org.apache.commons.codec.binary.Base64.decodeBase64(content));

			return new String(original);
			
		} catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}

    public static String encryptServiceKeys(ServiceMeta service) {
        ObjectMapper mapper = new ObjectMapper();

        try {
            String locationsJson = mapper.writeValueAsString(service.getLocations());

            StringBuilder sb = new StringBuilder();
            String plain = sb.append(service.getServiceId()).append("~")
                    .append(service.getFormId()).append("~")
                    .append(service.getTaskId()).append("~")
                    .append(service.getTaskType()).append("~")
                    .append(locationsJson).append("~")
                    .append(service.getServiceName())
                    .toString();

            String secretKey = APPLY_METADATA_ENC_KEY;

            String aesEncrypt = AESEncrypt(plain, secretKey);

            String signature = HMACSHA256(aesEncrypt, secretKey);
            return aesEncrypt.concat(".").concat(signature);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static ServiceMeta decryptServiceKeys(String serviceKey) {
        if (serviceKey.contains("%2B")) {
            serviceKey = serviceKey.replace("%2B", "+");
        }
        if (serviceKey.contains(" ")) {
            serviceKey = serviceKey.replaceAll(" ", "+");
        }


        String[] parts = serviceKey.split("\\.");
        String encrypted = parts[0];
        String signature = parts[1];

        String secretKey = APPLY_METADATA_ENC_KEY;

        if (!HMACSHA256(encrypted, secretKey).equals(signature)) {
            throw new RuntimeException("Invalid token signature");
        }

        String decrypt = AESDecrypt(encrypted, secretKey);
        String[] applyData = decrypt.split("~");

        ServiceMeta service = new ServiceMeta();
        service.setServiceId(Integer.parseInt(applyData[0]));
        service.setBaseServiceId(Integer.parseInt(applyData[0]) / 10000);
        service.setFormId(applyData[1]);
        service.setTaskId(applyData[2]);
        service.setTaskType(applyData[3]);
        service.setServiceName(applyData[5]);
        service.setServiceKey(serviceKey);

        String locationsJson = applyData[4];
        ObjectMapper mapper = new ObjectMapper();

        try {
            List<ServiceMeta.AvailableApplyLocations> locations =
                    mapper.readValue(locationsJson, new TypeReference<>() {});
            service.setLocations(locations);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return service;
    }


    public static String getClientIpAddr(ServerHttpRequest request) {
		String ip = "";
		if (request != null) {
			ip = getHeaderValue("X-Forwarded-For",request);
			if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
				ip = getHeaderValue("Proxy-Client-IP",request);
			}
			if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
				ip = getHeaderValue("WL-Proxy-Client-IP",request);
			}
			if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
				ip = getHeaderValue("HTTP_CLIENT_IP",request);
			}
			if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
				ip = getHeaderValue("HTTP_X_FORWARDED_FOR",request);
			}
			if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
				InetSocketAddress remoteAddress = request.getRemoteAddress();
				ip = remoteAddress.getAddress().getHostAddress();
			}
			
		}
		return ip;
	}
	
	public static String getHeaderValue(String key,ServerHttpRequest exchange) {
		HttpHeaders headers = exchange.getHeaders();
		List<String> value = headers.get(key);
    	if(value!=null && !value.isEmpty()) {
    		return value.get(0);
    	}
    	return "";
	}

    @SuppressWarnings("unchecked")
    public static <T> Mono<T> handleWebClientError(WebClientResponseException ex,String txnId) {

        HttpStatusCode status = ex.getStatusCode();
        applicationFlowLogs.error("Client error for txnId {} from downstream: {}",txnId,ex.getResponseBodyAsString());
        Map<String,Object> res = (Map<String, Object>) stringToEntity(ex.getResponseBodyAsString(), Map.class);

        String message =  res.containsKey("message") ? (String)res.get("message") : (String)res.get("errorMessage");
        Map<String, Object> data = res.containsKey("data") ? (Map<String, Object>) res.get("data") : null;

        SPRuntimeError error = getSpRuntimeError(status, message,txnId);
        error.setData(data);
        return Mono.error(error);

    }

    public static SPRuntimeError getSpRuntimeError(HttpStatusCode status, String message,String txnId) {
        SPRuntimeError error;

        if (status.is4xxClientError()) {
            error = new SPRuntimeError(
                    message.concat(" [DOWN-ERROR-001]"),
                    HttpStatus.valueOf(status.value()),txnId
            );
        } else if (status.is5xxServerError()) {
            error = new SPRuntimeError(
                    message.concat(" [DOWN-ERROR-002]"),
                    HttpStatus.BAD_GATEWAY,txnId
            );
        } else {
            error = new SPRuntimeError(
                    message.concat(" [DOWN-ERROR-003]"),
                    HttpStatus.BAD_GATEWAY,txnId
            );
        }
        return error;
    }

    public static SecretKey getMasterKey() {
        String masterKeyBase64 = System.getenv("MASTER_KEY_BASE64");
        byte[] decoded = Base64.getDecoder().decode(masterKeyBase64);
        return new SecretKeySpec(decoded, "AES");
    }

    public static SecretKey generateRandomAESKey() throws Exception {
        javax.crypto.KeyGenerator keyGen = javax.crypto.KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey();
    }

    public static String URLEncode(String data){
        return URLEncoder.encode(data,StandardCharsets.UTF_8);
    }

    public static String URLDecode(String data){
        return URLDecoder.decode(data,StandardCharsets.UTF_8);
    }

    public static Mono<ServerResponse> returnError(Exception ex, String txnId, Logger logger){
        Throwable actual = Exceptions.unwrap(ex);
        if (actual instanceof SPRuntimeError spr) {
            logger.warn("Error while saving form data: {}", spr.getMessage());
            return Mono.error(spr);
        }
        logger.error("Unexpected error while saving form data", ex);
        return Mono.error(new SPRuntimeError("Internal server error [SUB-500]", HttpStatus.INTERNAL_SERVER_ERROR,txnId));
    }

}
