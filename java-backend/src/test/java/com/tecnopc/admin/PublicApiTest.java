package com.tecnopc.admin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {PublicController.class, PaymentController.class}, properties = "MP_WEBHOOK_SECRET=test-secret")
@Import({SecurityConfig.class, CustomerChat.class, PublicCatalog.class})
class PublicApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean PaymentService payments;
    @MockitoBean CheckoutService checkout;
    @MockitoBean CustomerNotifications notifications;
    private static final AtomicInteger client = new AtomicInteger();

    private MockHttpServletRequestBuilder json(String path, String body) {
        int id = client.incrementAndGet();
        return post("/api/public/" + path).header("Idempotency-Key",java.util.UUID.randomUUID().toString()).contentType("application/json").content(body)
            .with(request -> { request.setRemoteAddr("192.0.2." + id); return request; });
    }

    @Test void catalogIsPublicAndExcludesPrivateColumns() throws Exception {
        when(jdbc.queryForList("SELECT id,nombre,categoria,sku,especificaciones,stock,precio FROM productos ORDER BY nombre LIMIT 2000"))
            .thenReturn(List.of(Map.of("id", 1, "nombre", "PC", "stock", 3, "precio", 100)));
        mvc.perform(get("/api/public/productos")).andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("PC"));
    }
    @Test void browserCanCreateValidatedRequest() throws Exception {
        when(jdbc.update(any(org.springframework.jdbc.core.PreparedStatementCreator.class),any(org.springframework.jdbc.support.KeyHolder.class)))
            .thenAnswer(call -> { ((org.springframework.jdbc.support.KeyHolder)call.getArgument(1)).getKeyList().add(Map.of("id",1L));return 1; });
        mvc.perform(json("solicitudes", "{\"tipo\":\"servicio\",\"nombre\":\"Ana\",\"telefono\":\"3001234567\",\"detalle\":\"No prende el equipo\"}")
            .header("Origin", "http://localhost:8081").header("Sec-Fetch-Site", "same-origin"))
            .andExpect(status().isCreated());
        verify(jdbc).update(any(org.springframework.jdbc.core.PreparedStatementCreator.class),any(org.springframework.jdbc.support.KeyHolder.class));
    }
    @Test void invalidRequestDoesNotWrite() throws Exception {
        mvc.perform(json("solicitudes", "{\"tipo\":\"admin\",\"nombre\":\"\",\"telefono\":\"1\",\"detalle\":\"a\"}"))
            .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(jdbc);
    }
    @Test void invalidTrackingCodeDoesNotQueryDatabase() throws Exception {
        mvc.perform(json("seguimiento", "{\"codigo\":\"1 OR 1=1\"}"))
            .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(jdbc);
    }
    @Test void trackingDoesNotExposeCustomerOrInternalNotes() throws Exception {
        when(jdbc.queryForList(anyString(), eq("TPC-2026-ABCDEFGH2345")))
            .thenReturn(List.of(Map.of("id", 4, "codigo_ingreso", "TPC-2026-ABCDEFGH2345", "equipo", "Laptop",
                "estado", "recibido", "creado_en", "2026-11-12", "actualizado_en", "2026-11-12")));
        mvc.perform(json("seguimiento", "{\"codigo\":\"TPC-2026-ABCDEFGH2345\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.equipo").value("Laptop"))
            .andExpect(jsonPath("$.id").doesNotExist()).andExpect(jsonPath("$.cliente_nombre").doesNotExist())
            .andExpect(jsonPath("$.diagnostico").doesNotExist());
    }
    @Test void unknownTrackingReturns404() throws Exception {
        mvc.perform(json("seguimiento", "{\"codigo\":\"TPC-2026-ABCDEFGH2345\"}"))
            .andExpect(status().isNotFound());
    }
    @Test void invalidNotificationContactIsRejectedBeforeWriting() throws Exception {
        mvc.perform(json("solicitudes", "{\"tipo\":\"servicio\",\"nombre\":\"Ana\",\"telefono\":\"3001234567\",\"detalle\":\"No prende el equipo\",\"contacto\":{\"correo\":\"invalid\",\"whatsapp\":\"3001234567\",\"avisosCorreo\":true,\"avisosWhatsapp\":true}}"))
            .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(jdbc,notifications);
    }
    @Test void consentWithoutDestinationIsRejected() throws Exception {
        mvc.perform(json("solicitudes", "{\"tipo\":\"servicio\",\"nombre\":\"Ana\",\"telefono\":\"3001234567\",\"detalle\":\"No prende el equipo\",\"contacto\":{\"avisosCorreo\":true}}"))
            .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(jdbc,notifications);
    }
    @Test void chatbotNeverQueriesSales() throws Exception {
        mvc.perform(json("chatbot", "{\"sesion\":\"session123\",\"mensaje\":\"mi compra CMP-20261112-ABC123\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.respuesta").value(org.hamcrest.Matchers.containsString("canal privado")));
        verifyNoInteractions(jdbc);
    }
    @Test void chatbotUsesConversationAndRejectsOversizedHistory() throws Exception {
        mvc.perform(json("chatbot", "{\"sesion\":\"session123\",\"mensaje\":\"para jugar\",\"historial\":[\"quiero armar una PC\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.respuesta").value(org.hamcrest.Matchers.containsString("presupuesto")));
        String history=java.util.stream.IntStream.range(0,7).mapToObj(i->"\"hola\"").collect(java.util.stream.Collectors.joining(","));
        mvc.perform(json("chatbot", "{\"sesion\":\"session123\",\"mensaje\":\"hola\",\"historial\":["+history+"]}"))
            .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(jdbc);
    }
    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "101", "null", "1.5"})
    void invalidPaymentQuantityIsRejected(String quantity) throws Exception {
        mvc.perform(json("pagos", "{\"cliente_nombre\":\"Ana\",\"items\":[{\"producto_id\":1,\"cantidad\":" + quantity + "}]}"))
            .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(payments);
    }
    @Test void validCheckoutUsesJavaService() throws Exception {
        when(checkout.start(any(),anyString())).thenReturn(Map.of("estado", "pendiente", "url_pago", "https://www.mercadopago.com.co/checkout"));
        mvc.perform(json("pagos", "{\"cliente_nombre\":\"Ana\",\"items\":[{\"producto_id\":1,\"cantidad\":2}]}")
            .header("Origin", "http://localhost:8081"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("pendiente"));
        verify(checkout).start(any(),anyString());
    }
    @Test void unsignedWebhookNeverContactsGateway() throws Exception {
        mvc.perform(post("/api/public/pagos/webhook").param("data.id", "123"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(payments);
    }
    @Test void formPostsAreRejected() throws Exception {
        mvc.perform(post("/api/public/solicitudes").contentType("application/x-www-form-urlencoded").content("tipo=servicio"))
            .andExpect(status().isUnsupportedMediaType());
    }
    @Test void oversizedBodyIsRejectedBeforeController() throws Exception {
        mvc.perform(json("solicitudes", "x".repeat(65537))).andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(jdbc);
    }
    @Test void trackingHasPerClientRateLimit() throws Exception {
        for (int i = 0; i < 15; i++) mvc.perform(post("/api/public/seguimiento").contentType("application/json")
            .content("{\"codigo\":\"TPC-2026-ABCDEFGH2345\"}")
            .with(request -> { request.setRemoteAddr("198.51.100.1"); return request; })).andExpect(status().isNotFound());
        mvc.perform(post("/api/public/seguimiento").contentType("application/json").content("{}")
            .with(request -> { request.setRemoteAddr("198.51.100.1"); return request; }))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
    }
    @Test void legacyPhpRouteIsInaccessible() throws Exception {
        mvc.perform(get("/backend/api/empleados.php")).andExpect(status().isUnauthorized());
    }
    @Test void springServesTheStoreWithoutAnotherWebServer() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("TecnoPC")));
        mvc.perform(get("/js/app.js")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/public")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(".php"))));
    }
    @Test void unknownPublicRoutesStayPrivate() throws Exception {
        mvc.perform(get("/api/public/empleados")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/public/productos").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void signedWebhookUsesSignedQueryIdInsteadOfBodyId() throws Exception {
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        String digest = java.util.HexFormat.of().formatHex(mac.doFinal("id:123;request-id:req-1;ts:1700000000;".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(payments.notifyPayment("123")).thenReturn(Map.of("ok", true));
        mvc.perform(post("/api/public/pagos/webhook").param("data.id", "123")
            .header("x-request-id", "req-1").header("x-signature", "ts=1700000000,v1=" + digest)
            .contentType("application/json").content("{\"data\":{\"id\":999}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        verify(payments).notifyPayment("123");
        verifyNoMoreInteractions(payments);
    }
}
