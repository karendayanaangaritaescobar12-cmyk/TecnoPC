package com.tecnopc.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual JDBC transactions on H2/MySQL mode; does not certify MySQL deployment. */
class PaymentTransactionTest {
    JdbcTemplate jdbc;
    MercadoPagoClient gateway;
    PaymentService service;
    private static final String REF = "TPC-PAY-" + "b".repeat(32);

    @BeforeEach void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE productos(id BIGINT PRIMARY KEY,nombre VARCHAR(80),precio DECIMAL(12,2),stock INT NOT NULL CHECK(stock>=0))");
        jdbc.execute("CREATE TABLE pagos_online(id BIGINT AUTO_INCREMENT PRIMARY KEY,referencia VARCHAR(64) UNIQUE,proveedor VARCHAR(30),estado VARCHAR(30),cliente_nombre VARCHAR(80),total_cop DECIMAL(12,2),proveedor_pago_id VARCHAR(100) UNIQUE,compra_id BIGINT UNIQUE)");
        jdbc.execute("CREATE TABLE pago_items(id BIGINT AUTO_INCREMENT PRIMARY KEY,pago_id BIGINT,producto_id BIGINT,nombre VARCHAR(80),cantidad INT,precio DECIMAL(12,2),FOREIGN KEY(pago_id) REFERENCES pagos_online(id),FOREIGN KEY(producto_id) REFERENCES productos(id))");
        jdbc.execute("CREATE TABLE compras(id BIGINT AUTO_INCREMENT PRIMARY KEY,numero VARCHAR(24) UNIQUE,cliente_nombre VARCHAR(80),subtotal DECIMAL(12,2),iva DECIMAL(12,2),total DECIMAL(12,2))");
        jdbc.execute("CREATE TABLE compra_detalles(id BIGINT AUTO_INCREMENT PRIMARY KEY,compra_id BIGINT,producto_id BIGINT,nombre_producto VARCHAR(80),cantidad INT CHECK(cantidad>0),precio_unitario DECIMAL(12,2),subtotal DECIMAL(12,2),FOREIGN KEY(compra_id) REFERENCES compras(id),FOREIGN KEY(producto_id) REFERENCES productos(id))");
        jdbc.update("INSERT INTO productos VALUES (1,'SSD',100.00,5),(2,'RAM',100.00,0)");
        jdbc.update("INSERT INTO pagos_online(id,referencia,proveedor,estado,cliente_nombre,total_cop) VALUES (7,?,'mercadopago','pendiente','Ana',119.00)", REF);
        jdbc.update("INSERT INTO pago_items(pago_id,producto_id,nombre,cantidad,precio) VALUES (7,1,'SSD',1,100.00)");
        gateway = mock(MercadoPagoClient.class);
        when(gateway.payment("123")).thenReturn(new ObjectMapper().readTree("{\"id\":123,\"external_reference\":\"" + REF +
            "\",\"currency_id\":\"COP\",\"transaction_amount\":119.00,\"status\":\"approved\"}"));
        service = new PaymentService(jdbc, gateway, new DataSourceTransactionManager(dataSource), mock(CustomerNotifications.class));
    }

    @Test void simultaneousDuplicateNotificationsDeductOnce() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return service.notifyPayment("123"); });
            var second = pool.submit(() -> { start.await(); return service.notifyPayment("123"); });
            start.countDown();
            assertEquals(true, first.get(10, TimeUnit.SECONDS).get("ok"));
            assertEquals(true, second.get(10, TimeUnit.SECONDS).get("ok"));
        }
        assertEquals(4, jdbc.queryForObject("SELECT stock FROM productos WHERE id=1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM compras", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM compra_detalles", Integer.class));
        assertEquals("aprobado", jdbc.queryForObject("SELECT estado FROM pagos_online WHERE id=7", String.class));
    }

    @Test void shortageInSecondProductLeavesAllStockUntouched() {
        jdbc.update("INSERT INTO pago_items(pago_id,producto_id,nombre,cantidad,precio) VALUES (7,2,'RAM',1,0.00)");
        assertEquals(true, service.notifyPayment("123").get("requiere_revision"));
        assertEquals(5, jdbc.queryForObject("SELECT stock FROM productos WHERE id=1", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM compras", Integer.class));
        assertEquals("revision", jdbc.queryForObject("SELECT estado FROM pagos_online WHERE id=7", String.class));
    }

    @Test void mismatchedAmountNeverCreatesSale() {
        jdbc.update("UPDATE pagos_online SET total_cop=238.00 WHERE id=7");
        service.notifyPayment("123");
        assertEquals(5, jdbc.queryForObject("SELECT stock FROM productos WHERE id=1", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM compras", Integer.class));
        assertEquals("pendiente", jdbc.queryForObject("SELECT estado FROM pagos_online WHERE id=7", String.class));
    }

    @Test void databaseFailureRollsBackSaleAndStock() {
        jdbc.execute("ALTER TABLE compra_detalles ADD CONSTRAINT reject_test_sale CHECK (cantidad>100)");
        assertThrows(org.springframework.dao.DataAccessException.class, () -> service.notifyPayment("123"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM compras", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT stock FROM productos WHERE id=1", Integer.class));
        assertEquals("pendiente", jdbc.queryForObject("SELECT estado FROM pagos_online WHERE id=7", String.class));
    }
    @Test void twoDifferentApprovedPaymentsCannotOversellTheLastUnit() throws Exception {
        jdbc.update("UPDATE productos SET stock=1 WHERE id=1");String other="TPC-PAY-"+"c".repeat(32);
        jdbc.update("INSERT INTO pagos_online(id,referencia,proveedor,estado,cliente_nombre,total_cop) VALUES (8,?,'mercadopago','pendiente','Otro cliente',119.00)",other);
        jdbc.update("INSERT INTO pago_items(pago_id,producto_id,nombre,cantidad,precio) VALUES (8,1,'SSD',1,100.00)");
        when(gateway.payment("124")).thenReturn(new ObjectMapper().readTree("{\"id\":124,\"external_reference\":\""+other+"\",\"currency_id\":\"COP\",\"transaction_amount\":119.00,\"status\":\"approved\"}"));
        var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->{start.await();return service.notifyPayment("123");});
            var second=executor.submit(()->{start.await();return service.notifyPayment("124");});start.countDown();
            first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
        }
        assertEquals(0,jdbc.queryForObject("SELECT stock FROM productos WHERE id=1",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM compras",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM pagos_online WHERE estado='aprobado'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM pagos_online WHERE estado='revision'",Integer.class));
    }
}
