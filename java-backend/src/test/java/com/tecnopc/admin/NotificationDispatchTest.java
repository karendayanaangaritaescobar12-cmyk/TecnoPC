package com.tecnopc.admin;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:dispatch-test;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","ADMIN_USER=dispatch-admin","ADMIN_PASSWORD=dispatch-test-password","tecnopc.notifications.initial-delay-ms=3600000"})
class NotificationDispatchTest {
 @Autowired JdbcTemplate jdbc;@Autowired CustomerNotifications notifications;@Autowired PlatformTransactionManager manager;
 @MockitoBean NotificationSender sender;
 @Autowired @org.springframework.beans.factory.annotation.Qualifier("notificationDispatchExecutor") org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor;
 final NotificationContact contact=new NotificationContact("prueba@example.invalid",null,true,false);
 @BeforeEach void clean(){jdbc.update("DELETE FROM notificaciones");}
 @AfterEach void awaitWorker()throws Exception{for(int i=0;i<100;i++){if(executor.getActiveCount()==0&&executor.getThreadPoolExecutor().getQueue().isEmpty())return;Thread.sleep(25);}fail("El proceso de avisos no terminó");}
 @Test void sendsAfterCommitWithoutWaitingForPeriodicSchedule()throws Exception {
  when(sender.readyChannels()).thenReturn(Set.of("correo"));when(sender.send(any())).thenReturn("provider-simulado");
  new TransactionTemplate(manager).executeWithoutResult(tx->{notifications.queue("ready-now",contact,"compra_lista","VEN-PRUEBA");verify(sender,never()).send(any());});
  waitForState("aceptado");verify(sender,times(1)).send(any());
 }
 @Test void rollbackNeverSendsOrLeavesQueuedMessages()throws Exception {
  new TransactionTemplate(manager).executeWithoutResult(tx->{notifications.queue("rollback",contact,"servicio_listo","TPC-PRUEBA");tx.setRollbackOnly();});
  Thread.sleep(150);verify(sender,never()).send(any());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notificaciones",Integer.class));
 }
 @Test void disabledChannelsRemainPendingWithoutSpendingAttempts()throws Exception {
  when(sender.readyChannels()).thenReturn(Set.of());
  new TransactionTemplate(manager).executeWithoutResult(tx->notifications.queue("disabled",contact,"compra_lista","VEN-PRUEBA"));
  verify(sender,timeout(3000).atLeastOnce()).readyChannels();verify(sender,never()).send(any());
  assertEquals("pendiente",jdbc.queryForObject("SELECT estado FROM notificaciones",String.class));assertEquals(0,jdbc.queryForObject("SELECT intentos FROM notificaciones",Integer.class));
 }
 private void waitForState(String state)throws Exception {for(int i=0;i<60;i++){if(jdbc.queryForObject("SELECT COUNT(*) FROM notificaciones WHERE estado=?",Integer.class,state)==1)return;Thread.sleep(50);}fail("El aviso no se procesó después del commit");}
}
