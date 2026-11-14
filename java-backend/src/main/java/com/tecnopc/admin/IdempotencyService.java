package com.tecnopc.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** The reservation, business writes and replay result commit or roll back together. */
@Service
public class IdempotencyService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;
    public IdempotencyService(JdbcTemplate jdbc,PlatformTransactionManager manager,ObjectMapper json){
        this.jdbc=jdbc;this.transaction=new TransactionTemplate(manager);
        this.json=json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }
    static String key(String value){
        if(value==null||!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Falta una clave Idempotency-Key UUID válida. Actualiza el cliente antes de registrar la operación.");
        return value.toLowerCase(Locale.ROOT);
    }
    public Map<String,Object> execute(String scope,String actor,String requestKey,Object payload,Supplier<Map<String,Object>> business){
        String key=key(requestKey);String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                json.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(payload)));}
        catch(Exception ex){throw new IllegalStateException("No se pudo identificar la operación",ex);}
        String principal=actor.toLowerCase(Locale.ROOT);
        var existing=read(scope,principal,key,hash);if(existing!=null)return existing;
        try {
            return transaction.execute(status->{
                jdbc.update("INSERT INTO operaciones_idempotentes(ambito,actor,clave,huella) VALUES (?,?,?,?)",scope,principal,key,hash);
                Map<String,Object> result=Objects.requireNonNull(business.get());
                try {
                    String response=json.writeValueAsString(result);
                    if(jdbc.update("UPDATE operaciones_idempotentes SET respuesta=? WHERE ambito=? AND actor=? AND clave=?",response,scope,principal,key)!=1)
                        throw new IllegalStateException("No se confirmó el resultado persistente de la operación");
                }catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("No se pudo guardar el resultado de la operación",ex);}
                return result;
            });
        }catch(DuplicateKeyException duplicate){
            // Read only after the failed transaction has rolled back. InnoDB may
            // otherwise retain its old snapshot or locks after a duplicate statement.
            var replay=read(scope,principal,key,hash);
            if(replay==null)throw duplicate;
            return replay;
        }
    }
    private Map<String,Object> read(String scope,String actor,String key,String hash){
        var rows=jdbc.queryForList("SELECT huella,respuesta FROM operaciones_idempotentes WHERE ambito=? AND actor=? AND clave=?",scope,actor,key);
        if(rows.isEmpty())return null;
        var row=rows.getFirst();
        if(!hash.equals(row.get("huella")))throw new ResponseStatusException(HttpStatus.CONFLICT,"La clave corresponde a otra solicitud. Conserva el intento original y comprueba su resultado.");
        if(row.get("respuesta")==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Operación en preparación. Conserva la misma clave para consultar nuevamente.");
        try{return json.readValue(row.get("respuesta").toString(),new TypeReference<LinkedHashMap<String,Object>>(){});}
        catch(Exception ex){throw new IllegalStateException("Resultado persistido inválido",ex);}
    }
}
