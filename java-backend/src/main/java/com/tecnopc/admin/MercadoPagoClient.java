package com.tecnopc.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MercadoPagoClient {
    private final ObjectMapper json;
    private final String token;
    private final String notificationUrl;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public MercadoPagoClient(ObjectMapper json, @Value("${MP_ACCESS_TOKEN:}") String token,
                             @Value("${MP_NOTIFICATION_URL:}") String notificationUrl) {
        this.json = json;
        this.token = token;
        this.notificationUrl = notificationUrl;
    }

    void checkConfigured() {
        try {
            URI uri = URI.create(notificationUrl);
            if (token.isBlank() || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw unavailable();
        }
    }

    JsonNode preference(String reference, BigDecimal total) {
        checkConfigured();
        var now = OffsetDateTime.now();
        return call("POST", "/checkout/preferences", Map.of(
            "items", List.of(Map.of("title", "Compra TecnoPC " + reference, "quantity", 1,
                "currency_id", "COP", "unit_price", total)),
            "external_reference", reference, "notification_url", notificationUrl, "expires", true,
            "expiration_date_from", now.toString(), "expiration_date_to", now.plusMinutes(30).toString()), reference);
    }

    JsonNode payment(String id) {
        if (!id.matches("[0-9]{1,30}")) throw new IllegalArgumentException("Invalid payment ID");
        return call("GET", "/v1/payments/" + id, null, null);
    }

    private JsonNode call(String method, String path, Object body, String key) {
        if (token.isBlank()) throw unavailable();
        try {
            var request = HttpRequest.newBuilder(URI.create("https://api.mercadopago.com" + path))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
            if (key != null) request.header("X-Idempotency-Key", key);
            if (body == null) request.GET();
            else request.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw unavailable();
            JsonNode result = json.readTree(response.body());
            if (result == null || !result.isObject()) throw unavailable();
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | IllegalArgumentException ex) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Pasarela de pagos no disponible o no configurada");
    }
}
