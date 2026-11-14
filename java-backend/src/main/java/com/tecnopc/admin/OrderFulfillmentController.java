package com.tecnopc.admin;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/vendedor/pedidos")
public class OrderFulfillmentController {
    private final JdbcTemplate jdbc;private final CustomerNotifications notifications;
    public OrderFulfillmentController(JdbcTemplate jdbc,CustomerNotifications notifications) { this.jdbc=jdbc;this.notifications=notifications; }

    @GetMapping
    public List<Map<String,Object>> pending() {
        return jdbc.queryForList("SELECT id,numero,cliente_nombre,preparacion,creado_en FROM compras WHERE estado='registrada' AND preparacion='preparando' ORDER BY id LIMIT 200");
    }

    @PostMapping("/{id}/listo")
    @Transactional
    public Map<String,Object> ready(@PathVariable long id) {
        var rows=jdbc.queryForList("SELECT numero,estado,preparacion,contacto_json FROM compras WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Compra no encontrada");
        var order=rows.getFirst();
        if(!"registrada".equals(order.get("estado"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"La compra no esta vigente");
        if("preparando".equals(order.get("preparacion"))) {
            jdbc.update("UPDATE compras SET preparacion='listo' WHERE id=?",id);
            notifications.queue("compra-lista:"+id,notifications.readContact(order.get("contacto_json")),"compra_lista",order.get("numero").toString());
        }
        return Map.of("mensaje","Compra lista","numero",order.get("numero"));
    }
}
