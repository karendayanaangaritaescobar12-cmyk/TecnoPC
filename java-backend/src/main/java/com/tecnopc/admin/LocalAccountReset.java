package com.tecnopc.admin;

import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit local maintenance input, never an HTTP endpoint or a packaged default. */
@Component @Order(1)
public class LocalAccountReset implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final String file;
    public LocalAccountReset(JdbcTemplate jdbc, TransactionTemplate transactions,
            @Value("${tecnopc.account-reset-file:}") String file) {
        this.jdbc=jdbc;this.transactions=transactions;this.file=file;
    }
    @Override public void run(ApplicationArguments args) throws Exception {
        if(file.isBlank() || !Files.exists(Path.of(file)))return;
        var hashes=new Properties();
        try(var reader=Files.newBufferedReader(Path.of(file))){hashes.load(reader);}
        transactions.executeWithoutResult(tx->{
            for(String role:List.of("ADMIN","VENDEDOR","TECNICO")) {
                String hash=hashes.getProperty(role,"");
                if(!hash.matches("\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}"))
                    throw new IllegalStateException("Archivo local de claves inválido");
                var users=jdbc.queryForList("SELECT id FROM usuarios WHERE rol=?",role);
                if(users.size()>1)throw new IllegalStateException("Hay varias cuentas para el rol "+role+"; selecciona la cuenta desde administración");
                if(users.isEmpty()) {
                    String name=switch(role){case "ADMIN"->"administrador";case "VENDEDOR"->"vendedor";default->"tecnico";};
                    jdbc.update("INSERT INTO usuarios(usuario,password_hash,rol,activo) VALUES (?,?,?,1)",name,hash,role);
                } else jdbc.update("UPDATE usuarios SET password_hash=? WHERE id=?",hash,users.getFirst().get("id"));
            }
        });
        Files.delete(Path.of(file));
    }
}
