package com.tecnopc.admin;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
    private final JdbcTemplate jdbc;
    private final MercadoPagoClient gateway;
    private final TransactionTemplate transaction;
    private final CustomerNotifications notifications;

    public PaymentService(JdbcTemplate jdbc, MercadoPagoClient gateway, PlatformTransactionManager manager, CustomerNotifications notifications) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.transaction = new TransactionTemplate(manager);
        this.notifications = notifications;
    }

    record Line(long productId, String name, int quantity, BigDecimal price) {}
    record Pending(String reference, BigDecimal total) {}

    Map<String, Object> start(PaymentController.Checkout checkout) {
        return paymentLink(createPending(checkout));
    }
    Pending createPending(PaymentController.Checkout checkout) {
        gateway.checkConfigured();
        TreeMap<Long, Integer> quantities = new TreeMap<>();
        for (var item : checkout.items()) {
            quantities.merge(item.productoId(), item.cantidad(), Integer::sum);
            if (quantities.get(item.productoId()) > 100) throw invalid("Cantidad excesiva");
        }
        Pending order = transaction.execute(status -> {
            List<Line> lines = new ArrayList<>();
            BigDecimal subtotal = BigDecimal.ZERO;
            for (var item : quantities.entrySet()) {
                var rows = jdbc.queryForList("SELECT id,nombre,precio,stock FROM productos WHERE id=?", item.getKey());
                if (rows.isEmpty() || ((Number) rows.getFirst().get("stock")).intValue() < item.getValue())
                    throw invalid("Producto sin disponibilidad");
                var product = rows.getFirst();
                BigDecimal price = money(product.get("precio"));
                if (price.signum() < 0) throw invalid("Precio invalido");
                subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(item.getValue())));
                lines.add(new Line(item.getKey(), product.get("nombre").toString(), item.getValue(), price));
            }
            BigDecimal total = subtotal.multiply(new BigDecimal("1.19")).setScale(2, RoundingMode.HALF_UP);
            if (total.signum() <= 0 || total.compareTo(new BigDecimal("9999999999.99")) > 0) throw invalid("Total invalido");
            String reference = "TPC-PAY-" + UUID.randomUUID().toString().replace("-", "");
            jdbc.update("INSERT INTO pagos_online(referencia,proveedor,cliente_nombre,total_cop) VALUES (?,'mercadopago',?,?)",
                reference, checkout.clienteNombre().trim(), total);
            Long id = jdbc.queryForObject("SELECT id FROM pagos_online WHERE referencia=?", Long.class, reference);
            notifications.saveContact("pagos_online",id,checkout.contacto());
            for (Line line : lines) jdbc.update("INSERT INTO pago_items(pago_id,producto_id,nombre,cantidad,precio) VALUES (?,?,?,?,?)",
                id, line.productId(), line.name(), line.quantity(), line.price());
            return new Pending(reference, total);
        });
        return order;
    }
    Map<String,Object> paymentLink(Pending order) {
        JsonNode preference = gateway.preference(order.reference(), order.total());
        String link = preference.path("init_point").asText("");
        try {
            URI uri = URI.create(link);
            String host = uri.getHost();
            // Redirect only to the provider's checkout domains, never arbitrary URLs.
            if (link.length()>2048 || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || host == null ||
                !(host.equals("www.mercadopago.com.co") || host.equals("sandbox.mercadopago.com.co")))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Enlace de pago no disponible");
        }
        return Map.of("referencia", order.reference(), "total", order.total(), "url_pago", link, "estado", "pendiente");
    }

    Map<String, Object> notifyPayment(String id) {
        JsonNode payment = gateway.payment(id);
        if (!id.equals(payment.path("id").asText())) return Map.of("ok", true);
        String reference = payment.path("external_reference").asText("");
        if (!reference.matches("TPC-PAY-[a-fA-F0-9]{24,32}")) return Map.of("ok", true);
        if (!payment.path("transaction_amount").isNumber()) return Map.of("ok", true);
        return transaction.execute(status -> {
            var orders = jdbc.queryForList("SELECT * FROM pagos_online WHERE referencia=? FOR UPDATE", reference);
            if (orders.isEmpty()) return Map.of("ok", true);
            var order = orders.getFirst();
            Object boundId = order.get("proveedor_pago_id");
            if (!"mercadopago".equals(order.get("proveedor")) || (boundId != null && !id.equals(boundId.toString())) ||
                !"COP".equals(payment.path("currency_id").asText()) ||
                payment.path("transaction_amount").decimalValue().compareTo(money(order.get("total_cop"))) != 0 ||
                "aprobado".equals(order.get("estado"))) return Map.of("ok", true);
            String state = payment.path("status").asText();
            if (state.equals("approved")) return approve(order, id);
            if (state.equals("rejected") || state.equals("cancelled"))
                jdbc.update("UPDATE pagos_online SET estado=?,proveedor_pago_id=? WHERE id=?",
                    state.equals("rejected") ? "rechazado" : "cancelado", id, order.get("id"));
            return Map.of("ok", true);
        });
    }

    private Map<String, Object> approve(Map<String, Object> order, String paymentId) {
        var lines = jdbc.queryForList("SELECT * FROM pago_items WHERE pago_id=? ORDER BY producto_id", order.get("id"));
        TreeMap<Long, Integer> quantities = new TreeMap<>();
        if (lines.isEmpty()) return review(order.get("id"), paymentId);
        for (var line : lines) {
            int quantity = ((Number) line.get("cantidad")).intValue();
            if (quantity < 1) return review(order.get("id"), paymentId);
            quantities.merge(((Number) line.get("producto_id")).longValue(), quantity, Math::addExact);
        }
        // Lock/check every product before modifying stock; shortage never leaves a partial sale.
        for (var entry : quantities.entrySet()) {
            var products = jdbc.queryForList("SELECT stock FROM productos WHERE id=? FOR UPDATE", entry.getKey());
            if (products.isEmpty() || ((Number) products.getFirst().get("stock")).intValue() < entry.getValue())
                return review(order.get("id"), paymentId);
        }
        BigDecimal total = money(order.get("total_cop"));
        BigDecimal subtotal = total.divide(new BigDecimal("1.19"), 2, RoundingMode.HALF_UP);
        String number = "MP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbc.update("INSERT INTO compras(numero,cliente_nombre,subtotal,iva,total) VALUES (?,?,?,?,?)",
            number, order.get("cliente_nombre"), subtotal, total.subtract(subtotal), total);
        Long saleId = jdbc.queryForObject("SELECT id FROM compras WHERE numero=?", Long.class, number);
        for (var line : lines) jdbc.update("INSERT INTO compra_detalles(compra_id,producto_id,nombre_producto,cantidad,precio_unitario,subtotal) VALUES (?,?,?,?,?,?)",
            saleId, line.get("producto_id"), line.get("nombre"), line.get("cantidad"), line.get("precio"),
            money(line.get("precio")).multiply(new BigDecimal(line.get("cantidad").toString())).setScale(2, RoundingMode.HALF_UP));
        for (var entry : quantities.entrySet())
            if (jdbc.update("UPDATE productos SET stock=stock-? WHERE id=? AND stock>=?", entry.getValue(), entry.getKey(), entry.getValue()) != 1)
                throw new IllegalStateException("Stock changed during locked transaction");
        jdbc.update("UPDATE pagos_online SET estado='aprobado',proveedor_pago_id=?,compra_id=? WHERE id=?", paymentId, saleId, order.get("id"));
        var contact=notifications.readContact(order.get("contacto_json"));
        notifications.saveContact("compras",saleId,contact);
        notifications.queue("compra:"+saleId,contact,"compra_registrada",number);
        return Map.of("ok", true);
    }

    private Map<String, Object> review(Object id, String paymentId) {
        jdbc.update("UPDATE pagos_online SET estado='revision',proveedor_pago_id=? WHERE id=?", paymentId, id);
        return Map.of("ok", true, "requiere_revision", true);
    }

    private BigDecimal money(Object value) { return new BigDecimal(value.toString()); }
    private ResponseStatusException invalid(String reason) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
    }
}
