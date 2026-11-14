package com.tecnopc.admin;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WebhookSignatureTest {
    private String signature() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String digest = HexFormat.of().formatHex(mac.doFinal("id:123;request-id:req-1;ts:1700000000;".getBytes(StandardCharsets.UTF_8)));
        return "ts=1700000000,v1=" + digest;
    }
    @Test void acceptsCorrectSignature() throws Exception {
        assertTrue(WebhookSignature.valid("test-secret", "123", "req-1", signature()));
    }
    @Test void changingPaymentIdInvalidatesSignature() throws Exception {
        assertFalse(WebhookSignature.valid("test-secret", "124", "req-1", signature()));
    }
    @Test void changingRequestIdInvalidatesSignature() throws Exception {
        assertFalse(WebhookSignature.valid("test-secret", "123", "req-2", signature()));
    }
    @Test void wrongSecretIsRejected() throws Exception {
        assertFalse(WebhookSignature.valid("different-secret", "123", "req-1", signature()));
    }
    @Test void absentSecretAndMalformedInputsAreRejected() {
        assertFalse(WebhookSignature.valid("", "123", "req-1", "ts=1,v1=" + "0".repeat(64)));
        assertFalse(WebhookSignature.valid("s", "../123", "req-1", "ts=1,v1=" + "0".repeat(64)));
        assertFalse(WebhookSignature.valid("s", "123", "req-1", "ts=1,ts=2,v1=" + "0".repeat(64)));
        assertFalse(WebhookSignature.valid("s", "123", "req-1", null));
    }
}
