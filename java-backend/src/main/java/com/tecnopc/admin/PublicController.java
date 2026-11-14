package com.tecnopc.admin;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.support.GeneratedKeyHolder;

@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final JdbcTemplate jdbc;
    private final CustomerChat chat;
    private final CustomerNotifications notifications;
    private final PublicCatalog catalog;

    public PublicController(JdbcTemplate jdbc, CustomerChat chat, CustomerNotifications notifications, PublicCatalog catalog) {
        this.jdbc = jdbc;
        this.chat = chat;
        this.notifications = notifications;
        this.catalog = catalog;
    }

    record Solicitud(@NotBlank @Pattern(regexp = "servicio|cotizacion") String tipo,
                     @NotBlank @Size(max = 80) String nombre,
                     @NotBlank @Size(min = 7, max = 30) String telefono,
                     @NotBlank @Size(min = 5, max = 600) String detalle,
                     @Valid NotificationContact contacto) {}
    record Seguimiento(@NotBlank @Pattern(regexp = "TPC-[0-9]{4}-[A-HJ-NP-Z2-9]{12}") String codigo) {}
    record Consulta(@NotBlank @Size(max = 500) String mensaje,
                    @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{8,64}") String sesion,
                    @Size(max=6) List<@NotBlank @Size(max=500) String> historial) {}

    @GetMapping("/productos")
    public List<Map<String, Object>> productos(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","public, max-age=5, must-revalidate");
        return catalog.products();
    }

    @PostMapping("/solicitudes")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> solicitud(@Valid @RequestBody Solicitud data) {
        var key=new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement=connection.prepareStatement("INSERT INTO solicitudes(tipo,nombre,telefono,detalle) VALUES (?,?,?,?)",new String[]{"id"});
            statement.setString(1,data.tipo());statement.setString(2,data.nombre().trim());
            statement.setString(3,data.telefono().trim());statement.setString(4,data.detalle().trim());return statement;
        },key);
        Number id=key.getKey();
        if(id==null) throw new IllegalStateException("Missing generated request ID");
        notifications.saveContact("solicitudes",id.longValue(),data.contacto());
        notifications.queue("solicitud:"+id,data.contacto(),"solicitud_registrada","SOL-"+id);
        return Map.of("mensaje", "Solicitud registrada", "id", id.longValue());
    }

    @PostMapping("/seguimiento")
    public Map<String, Object> seguimiento(@Valid @RequestBody Seguimiento data) {
        var rows = jdbc.queryForList("SELECT id,codigo_ingreso,equipo,estado,creado_en,actualizado_en FROM reparaciones WHERE codigo_ingreso=? LIMIT 1", data.codigo());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos una reparacion con ese codigo");
        var order = rows.getFirst();
        var events = jdbc.queryForList("SELECT evento,creado_en FROM reparacion_eventos WHERE reparacion_id=? AND evento IN ('recibido','diagnostico','esperando_autorizacion','en_reparacion','listo','entregado','cancelado') ORDER BY id LIMIT 100", order.get("id"));
        return Map.of("codigo", order.get("codigo_ingreso"), "equipo", order.get("equipo"),
            "estado", order.get("estado"), "fechaIngreso", order.get("creado_en"),
            "ultimaActualizacion", order.get("actualizado_en"), "historial", events);
    }

    @PostMapping("/chatbot")
    public Map<String, Object> chatbot(@Valid @RequestBody Consulta data) {
        return chat.answer(data.mensaje(),data.historial());
    }
}
