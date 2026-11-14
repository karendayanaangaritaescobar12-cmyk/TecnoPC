package com.tecnopc.admin;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class WebhookSignature {
    private WebhookSignature() {}

    static boolean valid(String secret, String id, String requestId, String signature) {
        if (secret == null || secret.isBlank() || id == null || !id.matches("[0-9]{1,30}") ||
            requestId == null || !requestId.matches("[A-Za-z0-9._-]{1,200}") || signature == null || signature.length() > 1000)
            return false;
        var fields = new HashMap<String, String>();
        for (String item : signature.split(",")) {
            String[] pair = item.trim().split("=", 2);
            if (pair.length != 2 || fields.putIfAbsent(pair[0], pair[1]) != null) return false;
        }
        String ts = fields.getOrDefault("ts", "");
        String digest = fields.getOrDefault("v1", "");
        if (!ts.matches("[0-9]{1,16}") || !digest.matches("[a-fA-F0-9]{64}")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(("id:" + id + ";request-id:" + requestId + ";ts:" + ts + ";").getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(digest));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return false;
        }
    }
}
