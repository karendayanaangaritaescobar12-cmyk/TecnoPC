package com.tecnopc.admin;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real HTTP sockets and a bounded Hikari/H2 pool; no TiDB or external provider. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:resilience;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.hikari.maximum-pool-size=2","spring.datasource.hikari.connection-timeout=300",
    "ADMIN_USER=resilience-admin","ADMIN_PASSWORD=long-resilience-test-password",
    "EMAIL_ENABLED=false","WHATSAPP_ENABLED=false","tecnopc.notifications.initial-delay-ms=3600000"})
class ResilienceHttpTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource datasource;
    @MockitoBean CustomerChat chat;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private HttpRequest get(String path){return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10)).GET().build();}
    private HttpRequest chat(){return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/public/chatbot"))
            .timeout(Duration.ofSeconds(15)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"mensaje\":\"Consulta de prueba\",\"sesion\":\"session-test\",\"historial\":[]}")).build();}

    @Test void aBurstOf32UsersIsRejectedEarlyWithoutBlockingThePageOrLiveness() throws Exception {
        var entered=new CountDownLatch(6);var release=new CountDownLatch(1);
        var active=new AtomicInteger();var peak=new AtomicInteger();
        when(chat.answer(anyString(),anyList())).thenAnswer(call->{
            peak.accumulateAndGet(active.incrementAndGet(),Math::max);entered.countDown();
            try{assertTrue(release.await(10,TimeUnit.SECONDS));assertEquals(1,jdbc.queryForObject("SELECT 1",Integer.class));return Map.of("respuesta","Prueba");}
            finally{active.decrementAndGet();}
        });
        var requests=new ArrayList<CompletableFuture<HttpResponse<String>>>();
        for(int i=0;i<32;i++)requests.add(client.sendAsync(chat(),HttpResponse.BodyHandlers.ofString()));
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            var rejected=(HttpResponse<?>)CompletableFuture.anyOf(requests.toArray(CompletableFuture[]::new)).get(5,TimeUnit.SECONDS);
            assertEquals(503,rejected.statusCode());assertEquals("2",rejected.headers().firstValue("Retry-After").orElseThrow());
            assertEquals(200,client.send(get("/livez"),HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(200,client.send(get("/index.html"),HttpResponse.BodyHandlers.ofString()).statusCode());
        } finally {release.countDown();}
        int ok=0,overload=0;
        for(var request:requests){int status=request.get(10,TimeUnit.SECONDS).statusCode();if(status==200)ok++;else if(status==503)overload++;else fail("Unexpected HTTP "+status);}
        assertTrue(ok>0);assertTrue(overload>0);assertTrue(peak.get()<=6);
        assertEquals(200,client.send(get("/api/public/productos"),HttpResponse.BodyHandlers.ofString()).statusCode());
        System.out.println("RESILIENCIA: 32 solicitudes HTTP, "+ok+" atendidas, "+overload+" rechazadas controladamente, máximo concurrente "+peak.get());
    }
    @Test void exhaustedDatabasePoolReturns503AndRecoversAfterConnectionsAreReleased() throws Exception {
        when(chat.answer(anyString(),anyList())).thenAnswer(call->{jdbc.queryForObject("SELECT 1",Integer.class);return Map.of("respuesta","Disponible");});
        try(var first=datasource.getConnection();var second=datasource.getConnection()){
            long started=System.nanoTime();var response=client.send(chat(),HttpResponse.BodyHandlers.ofString());
            assertEquals(503,response.statusCode());assertTrue(Duration.ofNanos(System.nanoTime()-started).toSeconds()<3);
            assertEquals("5",response.headers().firstValue("Retry-After").orElseThrow());
            assertFalse(response.body().contains("jdbc:"));assertFalse(response.body().contains("Hikari"));
            assertEquals(200,client.send(get("/livez"),HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        assertEquals(200,client.send(chat(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void actualServerDeliversSecurityHeadersAndRejectsOversizedInternalJson() throws Exception {
        var page=client.send(get("/index.html"),HttpResponse.BodyHandlers.ofString());
        assertTrue(page.headers().firstValue("Content-Security-Policy").orElseThrow().contains("script-src 'self'"));
        assertEquals("no-referrer",page.headers().firstValue("Referrer-Policy").orElseThrow());
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/admin/productos"))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("x".repeat(65537))).build();
        assertEquals(413,client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
