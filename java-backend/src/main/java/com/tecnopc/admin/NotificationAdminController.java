package com.tecnopc.admin;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/notificaciones")
public class NotificationAdminController {
    private final JdbcTemplate jdbc;private final NotificationSender sender;
    public NotificationAdminController(JdbcTemplate jdbc,NotificationSender sender) { this.jdbc=jdbc;this.sender=sender; }
    @GetMapping
    public List<Map<String,Object>> list() {
        return jdbc.queryForList("SELECT id,canal,evento,referencia,estado,intentos,ultimo_error,creado_en,aceptado_en FROM notificaciones ORDER BY creado_en DESC LIMIT 200");
    }
    @GetMapping("/canales")
    public Map<String,Object> channels() { return Map.of("habilitados",sender.readyChannels()); }
    record Retry(boolean confirmarPosibleDuplicado) {}
    @PostMapping("/{id}/reintentar")
    public Map<String,Object> retry(@PathVariable String id,@RequestBody Retry retry) {
        int count=jdbc.update("UPDATE notificaciones SET estado='pendiente',intentos=0,siguiente_intento=CURRENT_TIMESTAMP,bloqueo_hasta=NULL,lease_token=NULL WHERE id=? AND (estado='fallido' OR (estado='revision' AND ?))",
            id,retry.confirmarPosibleDuplicado());
        if(count!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"No admite reintento o requiere confirmar posible duplicado");
        return Map.of("mensaje","Reintento encolado");
    }
}
