package com.tecnopc.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class NotificationWorker {
    private final JdbcTemplate jdbc;private final NotificationSender sender;private final TransactionTemplate transaction;
    public NotificationWorker(JdbcTemplate jdbc,NotificationSender sender,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.sender=sender;this.transaction=new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString="${tecnopc.notifications.interval-ms:20000}",initialDelayString="${tecnopc.notifications.initial-delay-ms:20000}")
    public void drain() {
        Set<String> channels=sender.readyChannels();
        if(channels.isEmpty()) return;
        for(int i=0;i<10;i++) {
            var job=claim(channels);
            if(job==null) return;
            try {
                String providerId=sender.send(job);
                jdbc.update("UPDATE notificaciones SET estado='aceptado',proveedor_id=?,aceptado_en=CURRENT_TIMESTAMP,bloqueo_hasta=NULL,ultimo_error=NULL WHERE id=? AND lease_token=?",
                    providerId,job.id(),job.lease());
            } catch(NotificationSender.SendFailure ex) {
                String state=ex.ambiguous?"revision":ex.retry && job.attempts()<5?"pendiente":"fallido";
                var next=Timestamp.from(Instant.now().plusSeconds(60L << Math.min(job.attempts()-1,4)));
                jdbc.update("UPDATE notificaciones SET estado=?,siguiente_intento=?,ultimo_error=?,bloqueo_hasta=NULL WHERE id=? AND lease_token=?",
                    state,next,ex.code,job.id(),job.lease());
            }
        }
    }

    private NotificationSender.Job claim(Set<String> channels) {
        return transaction.execute(status -> {
            var now=Timestamp.from(Instant.now());
            // A crashed WhatsApp send may have reached Meta. Never resend it blindly.
            jdbc.update("UPDATE notificaciones SET estado='revision',ultimo_error='ENVIO_AMBIGUO' WHERE estado='enviando' AND bloqueo_hasta<? AND (canal='whatsapp' OR intentos>=5 OR creado_en<?)",
                now,Timestamp.from(Instant.now().minusSeconds(23*3600)));
            jdbc.update("UPDATE notificaciones SET estado='revision',ultimo_error='VENTANA_REINTENTO_VENCIDA' WHERE canal='correo' AND estado='pendiente' AND intentos>0 AND creado_en<?",
                Timestamp.from(Instant.now().minusSeconds(23*3600)));
            // TiDB does not implement SKIP LOCKED. Hold the row lock only while claiming.
            var rows=jdbc.queryForList("SELECT * FROM notificaciones WHERE (estado='pendiente' OR (estado='enviando' AND bloqueo_hasta<?)) AND siguiente_intento<=? AND intentos<5 AND canal IN (?,?) ORDER BY creado_en,id LIMIT 1 FOR UPDATE",
                now,now,channels.contains("correo")?"correo":"",channels.contains("whatsapp")?"whatsapp":"");
            if(rows.isEmpty()) return null;
            var row=rows.getFirst();String lease=UUID.randomUUID().toString();
            String id=row.get("id").toString();
            jdbc.update("UPDATE notificaciones SET estado='enviando',intentos=intentos+1,bloqueo_hasta=?,lease_token=? WHERE id=?",
                Timestamp.from(Instant.now().plusSeconds(120)),lease,id);
            return new NotificationSender.Job(id,lease,row.get("canal").toString(),row.get("destino").toString(),
                row.get("evento").toString(),row.get("referencia").toString(),((Number)row.get("intentos")).intValue()+1);
        });
    }
}
