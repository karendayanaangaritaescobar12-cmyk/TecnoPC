package com.tecnopc.admin;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicCatalogTest {
    private static final class TestClock extends Clock {
        private Instant now=Instant.parse("2026-11-13T18:00:00Z");
        void advance(long seconds){now=now.plusSeconds(seconds);}
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
    }
    @Test void sharesAnImmutableSnapshotAndRefreshesExpiredStock() {
        var jdbc=mock(JdbcTemplate.class);var clock=new TestClock();var catalog=new PublicCatalog(jdbc,clock);
        var row=new HashMap<String,Object>();row.put("id",1);row.put("stock",3);row.put("sku",null);
        when(jdbc.queryForList(PublicCatalog.SQL)).thenReturn(List.of(row),List.of(Map.of("id",1,"stock",2)));
        for(int i=0;i<100;i++)assertEquals(3,catalog.products().getFirst().get("stock"));
        verify(jdbc,times(1)).queryForList(PublicCatalog.SQL);
        assertThrows(UnsupportedOperationException.class,()->catalog.products().getFirst().put("stock",900));
        clock.advance(5);assertEquals(2,catalog.products().getFirst().get("stock"));
    }
    @Test void stopsRepeatedDatabaseAttemptsDuringFailureAndRecoversAfterBackoff() {
        var jdbc=mock(JdbcTemplate.class);var clock=new TestClock();var catalog=new PublicCatalog(jdbc,clock);
        when(jdbc.queryForList(PublicCatalog.SQL)).thenThrow(new DataAccessResourceFailureException("private-db-detail"))
                .thenReturn(List.of(Map.of("id",1,"stock",3)));
        for(int i=0;i<100;i++){
            var error=assertThrows(ResponseStatusException.class,catalog::products);
            assertEquals(503,error.getStatusCode().value());assertFalse(error.getReason().contains("private-db-detail"));
        }
        verify(jdbc,times(1)).queryForList(PublicCatalog.SQL);
        clock.advance(5);assertEquals(3,catalog.products().getFirst().get("stock"));
    }
    @Test void simultaneousColdRequestsNeverStartDuplicateDatabaseQueries() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var catalog=new PublicCatalog(jdbc);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(jdbc.queryForList(PublicCatalog.SQL)).thenAnswer(call->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return List.of(Map.of("id",1));});
        try(var executor=Executors.newSingleThreadExecutor()){
            var first=executor.submit(catalog::products);
            try{assertTrue(entered.await(5,TimeUnit.SECONDS));for(int i=0;i<100;i++)assertEquals(503,assertThrows(ResponseStatusException.class,catalog::products).getStatusCode().value());}
            finally{release.countDown();}
            assertEquals(1,first.get(5,TimeUnit.SECONDS).size());verify(jdbc,times(1)).queryForList(PublicCatalog.SQL);
        }
    }
}
