package com.tecnopc.admin;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Five-second public catalogue snapshot; never used to authorize a sale or payment. */
@Service
public class PublicCatalog {
    static final String SQL="SELECT id,nombre,categoria,sku,especificaciones,stock,precio FROM productos ORDER BY nombre LIMIT 2000";
    private record Snapshot(List<Map<String,Object>> products,long expires) {}
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ReentrantLock refresh=new ReentrantLock();
    private volatile Snapshot snapshot;
    private volatile long retryAt;
    @org.springframework.beans.factory.annotation.Autowired
    public PublicCatalog(JdbcTemplate jdbc){this(jdbc,Clock.systemUTC());}
    PublicCatalog(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    public List<Map<String,Object>> products(){
        long now=clock.millis();Snapshot current=snapshot;
        if(current!=null&&now<current.expires())return current.products();
        if(now<retryAt || !refresh.tryLock())throw unavailable();
        try {
            now=clock.millis();current=snapshot;
            if(current!=null&&now<current.expires())return current.products();
            if(now<retryAt)throw unavailable();
            try {
                var rows=jdbc.queryForList(SQL);
                var result=rows.stream().map(row->Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList();
                snapshot=new Snapshot(result,clock.millis()+5000);retryAt=0;return result;
            }catch(DataAccessException ex){retryAt=clock.millis()+5000;throw unavailable();}
        } finally {refresh.unlock();}
    }
    private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"El catálogo no está disponible temporalmente. Conservamos tu carrito; intenta más tarde.");}
}
