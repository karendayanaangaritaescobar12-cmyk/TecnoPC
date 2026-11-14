package com.tecnopc.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:idempotency-http;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
    "ADMIN_USER=idempotency-admin","ADMIN_PASSWORD=long-idempotency-test-password","MP_WEBHOOK_SECRET=test-secret",
    "EMAIL_ENABLED=false","WHATSAPP_ENABLED=false","tecnopc.notifications.initial-delay-ms=3600000"})
@AutoConfigureMockMvc
class IdempotencyHttpTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
    @MockitoBean MercadoPagoClient gateway;
    @Test void headersAreRequiredAndAnonymousClientsCannotReplayPrivateSales() throws Exception {
        String sale="{\"clienteNombre\":\"Ana\",\"items\":[{\"productoId\":1,\"cantidad\":1}]}";
        mvc.perform(post("/api/vendedor/v2/ventas").with(user("seller").roles("VENDEDOR")).contentType("application/json").content(sale)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/vendedor/v2/ventas").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(sale)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/public/v2/pagos").contentType("application/json").content("{\"cliente_nombre\":\"Ana\",\"items\":[{\"producto_id\":1,\"cantidad\":1}]}"))
            .andExpect(status().isBadRequest());verifyNoInteractions(gateway);
    }
    @Test void aPrivateSaleReplaysThroughHttpAndChangedDataReturns409() throws Exception {
        jdbc.update("INSERT INTO productos(id,nombre,categoria,precio,stock) VALUES (201,'Prueba HTTP','ssd',100,3)");
        String key=UUID.randomUUID().toString(),sale="{\"clienteNombre\":\"Ana\",\"items\":[{\"productoId\":201,\"cantidad\":1}]}";
        String original=null;
        for(int i=0;i<2;i++){
            var response=mvc.perform(post("/api/vendedor/v2/ventas").with(user("seller").roles("VENDEDOR"))
                    .header("Idempotency-Key",key).contentType("application/json").content(sale))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String number=json.readTree(response).path("numero").asText();if(original==null)original=number;else assertEquals(original,number);
        }
        mvc.perform(post("/api/vendedor/v2/ventas").with(user("seller").roles("VENDEDOR"))
                .header("Idempotency-Key",key).contentType("application/json").content(sale.replace("Ana","Otra persona")))
            .andExpect(status().isConflict());
        assertEquals(2,jdbc.queryForObject("SELECT stock FROM productos WHERE id=201",Integer.class));
    }
    @Test void publicCheckoutAndItsReceiptKeepTheSameOrderAndLinkOnHttpReplay() throws Exception {
        jdbc.update("INSERT INTO productos(id,nombre,categoria,precio,stock) VALUES (202,'Prueba checkout','ssd',100,3)");
        String link="https://sandbox.mercadopago.com.co/checkout/v1/redirect?pref_id=example";
        when(gateway.preference(anyString(),any())).thenReturn(json.createObjectNode().put("init_point",link));
        String key=UUID.randomUUID().toString(),body="{\"cliente_nombre\":\"Ana\",\"items\":[{\"producto_id\":202,\"cantidad\":1}]}";
        String reference=null;
        for(int i=0;i<2;i++){
            var response=mvc.perform(post("/api/public/v2/pagos").header("Origin","https://shop.example")
                    .header("Idempotency-Key",key).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.url_pago").value(link)).andReturn().getResponse().getContentAsString();
            String value=json.readTree(response).path("referencia").asText();if(reference==null)reference=value;else assertEquals(reference,value);
        }
        mvc.perform(post("/api/public/pagos/recibo").contentType("application/json").content("{\"referencia\":\""+reference+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.url_pago").value(link));
        verify(gateway,times(1)).preference(anyString(),any());
    }
}
