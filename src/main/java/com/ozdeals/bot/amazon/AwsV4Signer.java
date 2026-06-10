package com.ozdeals.bot.amazon;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class AwsV4Signer {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String SERVICE = "ProductAdvertisingAPI";
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String SIGNED_HEADERS = "content-encoding;content-type;host;x-amz-date;x-amz-target";
    private static final String TARGET = "com.amazon.paapi5.v1.ProductAdvertisingAPIv1.SearchItems";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String CONTENT_ENCODING = "amz-sdk-request";

    public static Map<String, String> sign(String accessKey, String secretKey, String region,
                                            String host, String path, String body) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = now.format(DATE_TIME_FMT);
        String dateStamp = now.format(DATE_FMT);

        try {
            String payloadHash = sha256Hex(body);

            String canonicalHeaders =
                    "content-encoding:" + CONTENT_ENCODING + "\n" +
                    "content-type:" + CONTENT_TYPE + "\n" +
                    "host:" + host + "\n" +
                    "x-amz-date:" + amzDate + "\n" +
                    "x-amz-target:" + TARGET + "\n";

            String canonicalRequest =
                    "POST\n" +
                    path + "\n" +
                    "\n" +
                    canonicalHeaders + "\n" +
                    SIGNED_HEADERS + "\n" +
                    payloadHash;

            String credentialScope = dateStamp + "/" + region + "/" + SERVICE + "/aws4_request";
            String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);

            byte[] signingKey = getSigningKey(secretKey, dateStamp, region);
            String signature = bytesToHex(hmacSha256(signingKey, stringToSign));

            String authorization = ALGORITHM +
                    " Credential=" + accessKey + "/" + credentialScope +
                    ", SignedHeaders=" + SIGNED_HEADERS +
                    ", Signature=" + signature;

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Encoding", CONTENT_ENCODING);
            headers.put("Content-Type", CONTENT_TYPE);
            headers.put("X-Amz-Date", amzDate);
            headers.put("X-Amz-Target", TARGET);
            headers.put("Authorization", authorization);
            return headers;

        } catch (Exception e) {
            throw new RuntimeException("Failed to sign PA-API request", e);
        }
    }

    private static byte[] getSigningKey(String secretKey, String dateStamp, String region) throws Exception {
        byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, SERVICE);
        return hmacSha256(kService, "aws4_request");
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
