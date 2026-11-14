package com.tecnopc.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock MercadoPagoClient gateway;
    @Mock PlatformTransactionManager manager;
    PaymentService service;
    private final ObjectMapper json = new ObjectMapper();
    private static final String REF = "TPC-PAY-" + "a".repeat(32);

    @BeforeEach void setup() {
        lenient().when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new PaymentService(jdbc, gateway, manager, mock(CustomerNotifications.class));
    }

    private Map<String, Object> order(String state) {
        return Map.of("id", 7L, "referencia", REF, "proveedor", "mercadopago", "estado", state,
            "cliente_nombre", "Ana", "total_cop", new BigDecimal("119.00"));
    }
    private void provider(String currency, String amount) throws Exception {
        when(gateway.payment("123")).thenReturn(json.readTree("{\"id\":123,\"external_reference\":\"" + REF +
            "\",\"currency_id\":\"" + currency + "\",\"transaction_amount\":" + amount + ",\"status\":\"approved\"}"));
    }
    private PaymentController.Checkout checkout(int quantity) {
        return new PaymentController.Checkout("Ana", List.of(new PaymentController.Item(1L, quantity)));
    }
    private void pending() {
        when(jdbc.queryForList("SELECT * FROM pagos_online WHERE referencia=? FOR UPDATE", REF)).thenReturn(List.of(order("pendiente")));
    }
    private void items() {
        when(jdbc.queryForList("SELECT * FROM pago_items WHERE pago_id=? ORDER BY producto_id", 7L))
            .thenReturn(List.of(Map.of("producto_id", 1L, "nombre", "SSD", "cantidad", 1, "precio", new BigDecimal("100.00"))));
    }

    @Test void checkoutUsesDatabasePriceAndDoesNotDeductStock() throws Exception {
        when(jdbc.queryForList("SELECT id,nombre,precio,stock FROM productos WHERE id=?", 1L))
            .thenReturn(List.of(Map.of("id", 1L, "nombre", "SSD", "precio", new BigDecimal("100"), "stock", 3)));
        when(jdbc.queryForObject(eq("SELECT id FROM pagos_online WHERE referencia=?"), eq(Long.class), anyString())).thenReturn(7L);
        when(gateway.preference(anyString(), eq(new BigDecimal("238.00"))))
            .thenReturn(json.readTree("{\"init_point\":\"https://www.mercadopago.com.co/checkout/v1/redirect?pref_id=test\"}"));
        var result = service.start(checkout(2));
        assertEquals(new BigDecimal("238.00"), result.get("total"));
        assertEquals("pendiente", result.get("estado"));
        verify(jdbc, never()).update(startsWith("UPDATE productos"), any(Object[].class));
        verify(manager).commit(any());
    }
    @Test void aggregatedQuantityLimitIsEnforced() {
        var data = new PaymentController.Checkout("Ana", List.of(new PaymentController.Item(1L, 60), new PaymentController.Item(1L, 60)));
        assertThrows(ResponseStatusException.class, () -> service.start(data));
        verifyNoInteractions(jdbc);
    }
    @Test void unavailableProductDoesNotCreateOrder() {
        assertThrows(ResponseStatusException.class, () -> service.start(checkout(1)));
        verify(gateway, never()).preference(anyString(), any());
        verify(manager).rollback(any());
    }
    @Test void wrongProviderIdDoesNotTouchDatabase() throws Exception {
        when(gateway.payment("123")).thenReturn(json.readTree("{\"id\":999}"));
        assertEquals(Map.of("ok", true), service.notifyPayment("123"));
        verifyNoInteractions(jdbc);
    }
    @Test void wrongAmountDoesNotWrite() throws Exception {
        provider("COP", "1.00"); pending();
        service.notifyPayment("123");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void wrongCurrencyDoesNotWrite() throws Exception {
        provider("USD", "119.00"); pending();
        service.notifyPayment("123");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void approvedOrderIsNeverDeductedAgain() throws Exception {
        provider("COP", "119.00");
        when(jdbc.queryForList("SELECT * FROM pagos_online WHERE referencia=? FOR UPDATE", REF)).thenReturn(List.of(order("aprobado")));
        service.notifyPayment("123");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(jdbc, never()).queryForList("SELECT * FROM pago_items WHERE pago_id=? ORDER BY producto_id", 7L);
    }
    @Test void shortageIsMarkedForReviewWithoutSaleOrStockChange() throws Exception {
        provider("COP", "119.00"); pending(); items();
        when(jdbc.queryForList("SELECT stock FROM productos WHERE id=? FOR UPDATE", 1L)).thenReturn(List.of(Map.of("stock", 0)));
        assertEquals(true, service.notifyPayment("123").get("requiere_revision"));
        verify(jdbc).update("UPDATE pagos_online SET estado='revision',proveedor_pago_id=? WHERE id=?", "123", 7L);
        verify(jdbc, never()).update(startsWith("INSERT INTO compras"), any(Object[].class));
        verify(jdbc, never()).update(startsWith("UPDATE productos"), any(Object[].class));
    }
    @Test void duplicateNotificationCreatesOneSale() throws Exception {
        provider("COP", "119.00"); items();
        when(jdbc.queryForList("SELECT * FROM pagos_online WHERE referencia=? FOR UPDATE", REF))
            .thenReturn(List.of(order("pendiente")), List.of(order("aprobado")));
        when(jdbc.queryForList("SELECT stock FROM productos WHERE id=? FOR UPDATE", 1L)).thenReturn(List.of(Map.of("stock", 3)));
        when(jdbc.queryForObject(eq("SELECT id FROM compras WHERE numero=?"), eq(Long.class), anyString())).thenReturn(8L);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        service.notifyPayment("123"); service.notifyPayment("123");
        verify(jdbc, times(1)).update("UPDATE productos SET stock=stock-? WHERE id=? AND stock>=?", 1, 1L, 1);
        verify(jdbc, times(1)).update("UPDATE pagos_online SET estado='aprobado',proveedor_pago_id=?,compra_id=? WHERE id=?", "123", 8L, 7L);
    }
    @Test void failedStockUpdateRollsBackSale() throws Exception {
        provider("COP", "119.00"); pending(); items();
        when(jdbc.queryForList("SELECT stock FROM productos WHERE id=? FOR UPDATE", 1L)).thenReturn(List.of(Map.of("stock", 3)));
        when(jdbc.queryForObject(eq("SELECT id FROM compras WHERE numero=?"), eq(Long.class), anyString())).thenReturn(8L);
        assertThrows(IllegalStateException.class, () -> service.notifyPayment("123"));
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }
}
