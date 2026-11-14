package com.tecnopc.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Protected by the existing ADMIN route rule. Never returns URLs or credentials. */
@RestController
public class RuntimeDiagnostics {
    private final DataSource datasource;private final RuntimeCapacity capacity;
    public RuntimeDiagnostics(DataSource datasource,RuntimeCapacity capacity){this.datasource=datasource;this.capacity=capacity;}
    @GetMapping("/api/admin/diagnostico")
    public ResponseEntity<?> diagnostics(){
        var db=new LinkedHashMap<String,Object>();
        if(datasource instanceof com.zaxxer.hikari.HikariDataSource pool){
            db.put("maximo",pool.getMaximumPoolSize());var metrics=pool.getHikariPoolMXBean();
            if(metrics!=null){db.put("activas",metrics.getActiveConnections());db.put("libres",metrics.getIdleConnections());db.put("esperando",metrics.getThreadsAwaitingConnection());}
        }
        var runtime=Runtime.getRuntime();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("entrega","1.6.1","api",capacity.snapshot(),"conexiones",db,
                "memoriaUsadaBytes",runtime.totalMemory()-runtime.freeMemory(),"memoriaMaximaBytes",runtime.maxMemory()));
    }
}
