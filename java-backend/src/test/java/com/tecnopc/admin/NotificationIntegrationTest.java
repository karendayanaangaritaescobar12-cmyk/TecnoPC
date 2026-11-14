package com.tecnopc.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationIntegrationTest {
    JdbcTemplate jdbc;CustomerNotifications notifications;TransactionTemplate tx;Flyway flyway;
    DataSourceTransactionManager manager;
    NotificationSender sender;NotificationWorker worker;
    private final ObjectMapper json=new ObjectMapper();
    private final NotificationContact contact=new NotificationContact("cliente@example.com","+573001234567",true,true);

    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");
        flyway=Flyway.configure().dataSource(ds).load();flyway.migrate();
        jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        notifications=new CustomerNotifications(jdbc,json);
        sender=mock(NotificationSender.class);worker=new NotificationWorker(jdbc,sender,manager);
    }
    private long request() {
        return tx.execute(status -> ((Number)new PublicController(jdbc,new CustomerChat(jdbc),notifications,new PublicCatalog(jdbc)).solicitud(
            new PublicController.Solicitud("servicio","Ana","3001234567","No prende el equipo",contact)).get("id")).longValue());
    }
    private int count(String sql) { return jdbc.queryForObject(sql,Integer.class); }

    @Test void migrationsCreateCompleteSchemaAndCanRunAgain() {
        assertEquals(0,flyway.migrate().migrationsExecuted);
        assertEquals(0,count("SELECT COUNT(*) FROM usuarios"));
        assertEquals(0,count("SELECT COUNT(*) FROM reparaciones"));
        assertEquals(0,count("SELECT COUNT(*) FROM notificaciones"));
    }
    @Test void registrationAndTwoChannelEventsCommitTogether() {
        request();
        assertEquals(1,count("SELECT COUNT(*) FROM solicitudes"));
        assertEquals(2,count("SELECT COUNT(*) FROM notificaciones WHERE evento='solicitud_registrada' AND estado='pendiente'"));
        verifyNoInteractions(sender);
    }
    @Test void rollbackRemovesBusinessRowAndAllEvents() {
        assertThrows(IllegalStateException.class,()->tx.execute(status->{request();throw new IllegalStateException("Rollback test");}));
        assertEquals(0,count("SELECT COUNT(*) FROM solicitudes"));
        assertEquals(0,count("SELECT COUNT(*) FROM notificaciones"));
    }
    @Test void duplicateEventIsStoredOncePerChannel() {
        tx.execute(status->{notifications.queue("test:1",contact,"compra_registrada","VEN-1");notifications.queue("test:1",contact,"compra_registrada","VEN-1");return null;});
        assertEquals(2,count("SELECT COUNT(*) FROM notificaciones"));
    }
    @Test void noConsentMeansNoMessages() {
        tx.execute(status->{notifications.queue("test:1",new NotificationContact("cliente@example.com","+573001234567",false,false),"compra_registrada","VEN-1");return null;});
        assertEquals(0,count("SELECT COUNT(*) FROM notificaciones"));
    }
    @Test void configuredChannelsAreAcceptedByProviderOnlyOnce() {
        request();when(sender.readyChannels()).thenReturn(Set.of("correo","whatsapp"));
        when(sender.send(any())).thenReturn("provider-test-id");
        worker.drain();worker.drain();
        assertEquals(2,count("SELECT COUNT(*) FROM notificaciones WHERE estado='aceptado'"));
        verify(sender,times(2)).send(any());
    }
    @Test void disabledChannelsKeepQueueWithoutSpendingAttempts() {
        request();when(sender.readyChannels()).thenReturn(Set.of());worker.drain();
        assertEquals(2,count("SELECT COUNT(*) FROM notificaciones WHERE intentos=0 AND estado='pendiente'"));
        verify(sender,never()).send(any());
    }
    @Test void rateLimitSchedulesRetryWithoutLosingEvent() {
        request();when(sender.readyChannels()).thenReturn(Set.of("correo"));
        when(sender.send(any())).thenThrow(new NotificationSender.SendFailure("HTTP_429",true,false));
        worker.drain();worker.drain();
        assertEquals(1,count("SELECT COUNT(*) FROM notificaciones WHERE canal='correo' AND intentos=1 AND estado='pendiente' AND siguiente_intento>CURRENT_TIMESTAMP"));
        verify(sender,times(1)).send(any());
    }
    @Test void ambiguousWhatsAppResultRequiresReviewAndIsNotResent() {
        request();when(sender.readyChannels()).thenReturn(Set.of("whatsapp"));
        when(sender.send(any())).thenThrow(new NotificationSender.SendFailure("CONEXION_AMBIGUA",false,true));
        worker.drain();worker.drain();
        assertEquals(1,count("SELECT COUNT(*) FROM notificaciones WHERE canal='whatsapp' AND estado='revision'"));
        verify(sender,times(1)).send(any());
    }
    @Test void crashedWhatsAppSendIsNotAutomaticallyRepeated() {
        request();jdbc.update("UPDATE notificaciones SET estado='enviando',intentos=1,bloqueo_hasta=? WHERE canal='whatsapp'",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(300)));
        when(sender.readyChannels()).thenReturn(Set.of("whatsapp"));worker.drain();
        assertEquals(1,count("SELECT COUNT(*) FROM notificaciones WHERE canal='whatsapp' AND estado='revision'"));
        verify(sender,never()).send(any());
    }
    @Test void emailRetryAfterIdempotencyWindowNeedsReview() {
        request();jdbc.update("UPDATE notificaciones SET intentos=1,creado_en=? WHERE canal='correo'",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(2*86400)));
        when(sender.readyChannels()).thenReturn(Set.of("correo"));worker.drain();
        assertEquals(1,count("SELECT COUNT(*) FROM notificaciones WHERE canal='correo' AND estado='revision'"));
        verify(sender,never()).send(any());
    }
    @Test void markingPurchaseReadyTwiceQueuesOneEventPerChannel() {
        jdbc.update("INSERT INTO compras(numero,cliente_nombre,subtotal,iva,total) VALUES ('VEN-TEST','Ana',100,19,119)");
        Long id=jdbc.queryForObject("SELECT id FROM compras WHERE numero='VEN-TEST'",Long.class);
        var controller=new OrderFulfillmentController(jdbc,notifications);
        tx.execute(status->{notifications.saveContact("compras",id,contact);controller.ready(id);controller.ready(id);return null;});
        assertEquals(2,count("SELECT COUNT(*) FROM notificaciones WHERE evento='compra_lista'"));
        assertEquals("listo",jdbc.queryForObject("SELECT preparacion FROM compras WHERE id=?",String.class,id));
    }
    @Test void completeRepairQueuesReadyOnceAndNeverLeaksDiagnosisInMessage() {
        long requestId=request();
        var technician=new TecnicoController(jdbc,notifications);
        var auth=new UsernamePasswordAuthenticationToken("tecnico","unused");
        Long id=tx.execute(status->{var response=technician.recibir(new TecnicoController.Recepcion(requestId,"Laptop","Modelo",null,null),auth);return ((Number)((Map<?,?>)response.getBody()).get("id")).longValue();});
        assertEquals("en proceso",jdbc.queryForObject("SELECT estado FROM solicitudes WHERE id=?",String.class,requestId));
        tx.execute(status->{
            assertEquals(200,technician.actualizar(id,new TecnicoController.Actualizacion("diagnostico","Nota privada",null,new BigDecimal("100"),null),auth).getStatusCode().value());
            assertEquals(200,technician.actualizar(id,new TecnicoController.Actualizacion("esperando_autorizacion",null,null,null,null),auth).getStatusCode().value());
            assertEquals(200,technician.actualizar(id,new TecnicoController.Actualizacion("en_reparacion",null,null,null,true),auth).getStatusCode().value());
            assertEquals(200,technician.actualizar(id,new TecnicoController.Actualizacion("listo",null,"Trabajo privado",null,null),auth).getStatusCode().value());
            technician.actualizar(id,new TecnicoController.Actualizacion("listo",null,null,null,null),auth);return null;
        });
        assertEquals(2,count("SELECT COUNT(*) FROM notificaciones WHERE evento='servicio_listo'"));
        assertEquals("listo",jdbc.queryForObject("SELECT estado FROM solicitudes WHERE id=?",String.class,requestId));
        tx.execute(status->technician.actualizar(id,new TecnicoController.Actualizacion("entregado",null,null,null,null),auth));
        assertEquals("cerrado",jdbc.queryForObject("SELECT estado FROM solicitudes WHERE id=?",String.class,requestId));
        assertEquals(0,count("SELECT COUNT(*) FROM notificaciones WHERE referencia LIKE '%privad%'"));
    }
    @Test void transactionGuardRejectsEventsOutsideBusinessTransaction() {
        assertThrows(IllegalStateException.class,()->notifications.queue("bad",contact,"compra_lista","VEN-1"));
    }
    @Test void paymentApprovalPersistsContactAndQueuesRegistrationOnlyOnce() throws Exception {
        String ref="TPC-PAY-"+"c".repeat(32);
        jdbc.update("INSERT INTO productos(nombre,categoria,precio,stock) VALUES ('SSD','ssd',100,5)");
        Long product=jdbc.queryForObject("SELECT id FROM productos WHERE nombre='SSD'",Long.class);
        jdbc.update("INSERT INTO pagos_online(referencia,proveedor,cliente_nombre,total_cop) VALUES (?,'mercadopago','Ana',119)",ref);
        Long payment=jdbc.queryForObject("SELECT id FROM pagos_online WHERE referencia=?",Long.class,ref);
        tx.execute(status->{notifications.saveContact("pagos_online",payment,contact);return null;});
        jdbc.update("INSERT INTO pago_items(pago_id,producto_id,nombre,cantidad,precio) VALUES (?,?,'SSD',1,100)",payment,product);
        var gateway=mock(MercadoPagoClient.class);
        when(gateway.payment("123")).thenReturn(json.readTree("{\"id\":123,\"external_reference\":\""+ref+"\",\"currency_id\":\"COP\",\"transaction_amount\":119,\"status\":\"approved\"}"));
        var payments=new PaymentService(jdbc,gateway,manager,notifications);
        payments.notifyPayment("123");payments.notifyPayment("123");
        assertEquals(1,count("SELECT COUNT(*) FROM compras"));
        assertEquals(2,count("SELECT COUNT(*) FROM notificaciones WHERE evento='compra_registrada'"));
        assertNotNull(jdbc.queryForObject("SELECT contacto_json FROM compras",String.class));
    }
    @Test void twoWorkersCannotSendSameQueuedJobConcurrently() throws Exception {
        request();when(sender.readyChannels()).thenReturn(Set.of("correo","whatsapp"));when(sender.send(any())).thenReturn("test-id");
        var other=new NotificationWorker(jdbc,sender,manager);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->worker.drain());var second=pool.submit(()->other.drain());
            first.get(10,java.util.concurrent.TimeUnit.SECONDS);second.get(10,java.util.concurrent.TimeUnit.SECONDS);
        }
        verify(sender,times(2)).send(any());
        assertEquals(2,count("SELECT COUNT(*) FROM notificaciones WHERE estado='aceptado'"));
    }
}
