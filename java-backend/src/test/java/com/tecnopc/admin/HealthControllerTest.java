package com.tecnopc.admin;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthControllerTest {
    @Test void livenessNeverQueriesDatabaseAndReadinessCoalescesFailureAndRecovery() {
        var jdbc=mock(JdbcTemplate.class);
        var time=new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-11-13T18:00:00Z"));
        var clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return time.get();}};
        var health=new HealthController(jdbc,clock);
        for(int i=0;i<100;i++)assertEquals("vivo",health.live().get("estado"));verifyNoInteractions(jdbc);
        when(jdbc.queryForObject("SELECT 1",Integer.class)).thenThrow(new DataAccessResourceFailureException("private")) .thenReturn(1);
        for(int i=0;i<100;i++)assertEquals(503,health.health().getStatusCode().value());
        verify(jdbc,times(1)).queryForObject("SELECT 1",Integer.class);
        time.set(time.get().plusSeconds(5));assertEquals(200,health.health().getStatusCode().value());
    }
}
