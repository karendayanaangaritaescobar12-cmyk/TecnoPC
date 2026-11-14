package com.tecnopc.admin;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
    private final JdbcTemplate jdbc;
    private final java.time.Clock clock;
    private final java.util.concurrent.locks.ReentrantLock probe=new java.util.concurrent.locks.ReentrantLock();
    private record Result(boolean healthy,long expires){}
    private volatile Result result;
    @org.springframework.beans.factory.annotation.Autowired
    public HealthController(JdbcTemplate jdbc) { this(jdbc,java.time.Clock.systemUTC()); }
    HealthController(JdbcTemplate jdbc,java.time.Clock clock){this.jdbc=jdbc;this.clock=clock;}
    @GetMapping("/livez")
    public Map<String,String> live(){return Map.of("estado","vivo");}
    @GetMapping("/healthz")
    public ResponseEntity<?> health() {
        var current=result;long now=clock.millis();
        if(current!=null&&now<current.expires())return response(current.healthy());
        if(!probe.tryLock())return response(false);
        try {
            current=result;now=clock.millis();
            if(current!=null&&now<current.expires())return response(current.healthy());
            boolean healthy;
            try {healthy=Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1",Integer.class));}
            catch(org.springframework.dao.DataAccessException ex){healthy=false;}
            result=new Result(healthy,clock.millis()+5000);return response(healthy);
        } finally {probe.unlock();}
    }
    private ResponseEntity<?> response(boolean healthy){return ResponseEntity.status(healthy?200:503)
            .header("Cache-Control","no-store").body(Map.of("estado",healthy?"ok":"no_disponible"));}
}
