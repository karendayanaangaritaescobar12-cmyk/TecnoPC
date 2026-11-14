package com.tecnopc.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual SQL constraints/transactions; no real gateway requests. */
class IdempotentOperationsTest {
    JdbcTemplate jdbc;DataSourceTransactionManager manager;IdempotencyService idempotency;
    PaymentService payments;CheckoutService checkout;VendedorController sales;
    MercadoPagoClient gateway;ObjectMapper json=new ObjectMapper();CustomerNotifications notifications;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");
        Flyway.configure().dataSource(ds).load().migrate();jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
        jdbc.update("INSERT INTO productos(id,nombre,categoria,precio,stock) VALUES (1,'SSD','ssd',100.00,5)");
        idempotency=new IdempotencyService(jdbc,manager,json);notifications=new CustomerNotifications(jdbc,json);gateway=mock(MercadoPagoClient.class);
        payments=new PaymentService(jdbc,gateway,manager,notifications);checkout=new CheckoutService(idempotency,payments,jdbc,manager);
        sales=new VendedorController(jdbc,manager,notifications,idempotency);
        lenient().when(gateway.preference(anyString(),any())).thenReturn(json.readTree("{\"init_point\":\"https://sandbox.mercadopago.com.co/checkout/v1/redirect?pref_id=example\"}"));
    }
    private int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    private int stock(){return jdbc.queryForObject("SELECT stock FROM productos WHERE id=1",Integer.class);}
    private PaymentController.Checkout request(){return new PaymentController.Checkout("Ana",List.of(new PaymentController.Item(1L,1)));}
    private VendedorController.Venta sale(){return new VendedorController.Venta("Ana","",List.of(new VendedorController.Item(1L,1)),new NotificationContact("client@example.com","",true,false));}
    private UsernamePasswordAuthenticationToken user(){return UsernamePasswordAuthenticationToken.authenticated("seller",null,List.of());}

    @Test void aCommittedSaleReplaysAfterRestartEvenWhenStockAndPricesChanged() {
        String key=UUID.randomUUID().toString();var first=sales.venta(sale(),user(),key);
        jdbc.update("UPDATE productos SET stock=0,precio=900 WHERE id=1");
        var restarted=new VendedorController(jdbc,manager,notifications,new IdempotencyService(jdbc,manager,new ObjectMapper()));
        var replay=restarted.venta(sale(),user(),key.toUpperCase(Locale.ROOT));
        assertEquals(first.getBody().toString(),replay.getBody().toString());
        assertEquals(1,count("compras"));assertEquals(1,count("compra_detalles"));assertEquals(1,count("notificaciones"));assertEquals(0,stock());
    }
    @Test void changingTheRequestUnderTheSameKeyIsAConflict() {
        String key=UUID.randomUUID().toString();sales.venta(sale(),user(),key);
        var changed=new VendedorController.Venta("Otro cliente","",sale().items(),sale().contacto());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->sales.venta(changed,user(),key)).getStatusCode().value());
        assertEquals(1,count("compras"));assertEquals(4,stock());
    }
    @Test void concurrentSaleRetriesProduceOneSaleOneStockChangeAndOneNotification() throws Exception {
        String key=UUID.randomUUID().toString();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->{start.await();return sales.venta(sale(),user(),key);});
            var second=pool.submit(()->{start.await();return sales.venta(sale(),user(),key);});start.countDown();
            var a=(Map<?,?>)first.get(10,TimeUnit.SECONDS).getBody();var b=(Map<?,?>)second.get(10,TimeUnit.SECONDS).getBody();
            assertEquals(a.get("numero"),b.get("numero"));
        }
        assertEquals(1,count("compras"));assertEquals(1,count("operaciones_idempotentes"));assertEquals(4,stock());assertEquals(1,count("notificaciones"));
    }
    @Test void aFailedSaleRollsBackItsReservationAndCanBeRetriedWithTheSameKey() {
        String key=UUID.randomUUID().toString();jdbc.execute("ALTER TABLE compra_detalles ADD CONSTRAINT fail_write CHECK(cantidad>100)");
        assertThrows(org.springframework.dao.DataAccessException.class,()->sales.venta(sale(),user(),key));
        assertEquals(0,count("compras"));assertEquals(0,count("operaciones_idempotentes"));assertEquals(5,stock());
        jdbc.execute("ALTER TABLE compra_detalles DROP CONSTRAINT fail_write");
        assertEquals(201,sales.venta(sale(),user(),key).getStatusCode().value());assertEquals(4,stock());
    }
    @Test void differentKeysRepresentTwoIntentionallyDifferentSales() {
        sales.venta(sale(),user(),UUID.randomUUID().toString());sales.venta(sale(),user(),UUID.randomUUID().toString());
        assertEquals(2,count("compras"));assertEquals(3,stock());
    }
    @Test void accountAndScopeArePartOfTheUniqueIdentity() {
        String key=UUID.randomUUID().toString();var executions=new java.util.concurrent.atomic.AtomicInteger();
        for(String actor:List.of("seller","other-seller"))idempotency.execute("test",actor,key,Map.of("value",1),()->Map.of("value",executions.incrementAndGet()));
        idempotency.execute("other-scope","seller",key,Map.of("value",1),()->Map.of("value",executions.incrementAndGet()));
        assertEquals(3,executions.get());assertEquals(3,count("operaciones_idempotentes"));
    }
    @Test void checkoutReplaysTheSameOrderAndTheSameProviderLinkAfterRestart() {
        String key=UUID.randomUUID().toString();var first=checkout.start(request(),key);
        var restarted=new CheckoutService(new IdempotencyService(jdbc,manager,json),payments,jdbc,manager);
        var replay=restarted.start(request(),key);
        assertEquals(first.get("referencia"),replay.get("referencia"));assertEquals(first.get("url_pago"),replay.get("url_pago"));
        assertEquals(1,count("pagos_online"));assertEquals(1,count("pago_items"));assertEquals(5,stock());
        verify(gateway,times(1)).preference(anyString(),eq(new BigDecimal("119.00")));
    }
    @Test void concurrentCheckoutNeverMakesTwoProviderCallsAndDoesNotHoldSqlTransactionAcrossNetwork() throws Exception {
        String key=UUID.randomUUID().toString();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(gateway.preference(anyString(),any())).thenAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());entered.countDown();
            assertTrue(release.await(5,TimeUnit.SECONDS));return json.readTree("{\"init_point\":\"https://sandbox.mercadopago.com.co/checkout/v1/redirect?pref_id=example\"}");
        });
        try(var pool=Executors.newSingleThreadExecutor()){
            var first=pool.submit(()->checkout.start(request(),key));
            try {
                assertTrue(entered.await(5,TimeUnit.SECONDS));var pending=checkout.start(request(),key);
                assertEquals("",pending.get("url_pago"));assertEquals(1,count("pagos_online"));
            } finally {release.countDown();}
            var complete=first.get(5,TimeUnit.SECONDS);assertFalse(complete.get("url_pago").toString().isBlank());
            assertEquals(complete.get("referencia"),checkout.start(request(),key).get("referencia"));
        }
        verify(gateway,times(1)).preference(anyString(),any());
    }
    @Test void anAmbiguousProviderFailureIsPersistedForReviewAndNeverRetriedAutomatically() {
        when(gateway.preference(anyString(),any())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"timeout"));
        String key=UUID.randomUUID().toString();var result=checkout.start(request(),key);
        assertEquals("revision",result.get("estado"));assertEquals("",result.get("url_pago"));
        assertEquals(result.get("referencia"),checkout.start(request(),key).get("referencia"));
        assertEquals("revision",jdbc.queryForObject("SELECT estado FROM pagos_online",String.class));
        verify(gateway,times(1)).preference(anyString(),any());assertEquals(1,count("pagos_online"));
    }
    @Test void aCrashDuringProviderWorkMovesToReviewWithoutCreatingAnotherPreference() {
        String key=UUID.randomUUID().toString();var body=request();
        idempotency.execute("checkout","public",key,body,()->{
            var pending=payments.createPending(body);
            jdbc.update("INSERT INTO preferencias_checkout(referencia,estado,iniciada_en) VALUES (?,'creando',?)",pending.reference(),Timestamp.from(Instant.now().minusSeconds(120)));
            return Map.of("referencia",pending.reference(),"total",pending.total());
        });
        assertEquals("revision",checkout.start(body,key).get("estado"));
        verify(gateway,never()).preference(anyString(),any());assertEquals(1,count("pagos_online"));
    }
    @Test void missingOrMalformedKeysAreRejectedBeforeCreatingAnything() {
        for(String key:Arrays.asList(null,"","invalid","x".repeat(1000)))
            assertEquals(400,assertThrows(ResponseStatusException.class,()->checkout.start(request(),key)).getStatusCode().value());
        assertEquals(0,count("operaciones_idempotentes"));assertEquals(0,count("pagos_online"));verifyNoInteractions(gateway);
    }
    @Test void migrationPreservesAnExistingVersionTwoDatabase(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(ds).target("2").load().migrate();var old=new JdbcTemplate(ds);
        old.update("INSERT INTO productos(nombre,categoria,precio,stock) VALUES ('Existente','ssd',100,7)");
        old.update("INSERT INTO pagos_online(referencia,proveedor,cliente_nombre,total_cop) VALUES (?,'mercadopago','Cliente existente',119)","TPC-PAY-"+"a".repeat(32));
        assertEquals(1,Flyway.configure().dataSource(ds).load().migrate().migrationsExecuted);
        assertEquals(7,old.queryForObject("SELECT stock FROM productos",Integer.class));
        assertEquals(1,old.queryForObject("SELECT COUNT(*) FROM pagos_online",Integer.class));
    }
}
