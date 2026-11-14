package com.tecnopc.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CustomerNotifications {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final org.springframework.context.ApplicationEventPublisher events;
    public record PendingNotifications() {}
    public CustomerNotifications(JdbcTemplate jdbc, ObjectMapper json) { this(jdbc,json,null); }
    @org.springframework.beans.factory.annotation.Autowired
    public CustomerNotifications(JdbcTemplate jdbc, ObjectMapper json,org.springframework.context.ApplicationEventPublisher events) { this.jdbc=jdbc; this.json=json; this.events=events; }

    public void saveContact(String table, Object id, NotificationContact contact) {
        if (contact == null) return;
        if (!Set.of("solicitudes","compras","pagos_online").contains(table)) throw new IllegalArgumentException("Invalid table");
        requireTransaction();
        try { jdbc.update("UPDATE "+table+" SET contacto_json=? WHERE id=?",json.writeValueAsString(contact),id); }
        catch(JsonProcessingException ex) { throw new IllegalArgumentException("Invalid contact",ex); }
    }

    public NotificationContact readContact(Object value) {
        if(value==null) return null;
        try { return json.readValue(value.toString(),NotificationContact.class); }
        catch(JsonProcessingException ex) { throw new IllegalStateException("Invalid saved notification contact",ex); }
    }

    public void queue(String key, NotificationContact contact, String event, String reference) {
        if(contact==null) return;
        requireTransaction();
        if(contact.avisosCorreo()) insert(key+":correo","correo",contact.correo(),event,reference);
        if(contact.avisosWhatsapp()) insert(key+":whatsapp","whatsapp",contact.whatsapp(),event,reference);
        if(events!=null&&(contact.avisosCorreo()||contact.avisosWhatsapp()))events.publishEvent(new PendingNotifications());
    }

    private void insert(String key,String channel,String destination,String event,String reference) {
        jdbc.update("INSERT INTO notificaciones(id,evento_clave,canal,destino,evento,referencia) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE evento_clave=evento_clave",
            UUID.randomUUID().toString(),key,channel,destination,event,reference);
    }

    private void requireTransaction() {
        if(!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Notification events must share the business transaction");
    }
}
