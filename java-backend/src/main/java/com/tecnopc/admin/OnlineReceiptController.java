package com.tecnopc.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** The 128-bit random order reference is a private bearer capability, never a sequential ID. */
@RestController @RequestMapping("/api/public/pagos/recibo")
public class OnlineReceiptController {
 private final JdbcTemplate jdbc;
 public OnlineReceiptController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 record Lookup(@NotBlank @Pattern(regexp="TPC-PAY-[a-fA-F0-9]{32}") String referencia){}
 @PostMapping @Transactional(readOnly=true)
 public ResponseEntity<?> receipt(@Valid @RequestBody Lookup request){
  var orders=jdbc.queryForList("SELECT p.id,p.referencia,p.cliente_nombre,p.estado,p.total_cop,p.creado_en,c.numero AS numero_venta,c.preparacion FROM pagos_online p LEFT JOIN compras c ON c.id=p.compra_id WHERE p.referencia=?",request.referencia());
  if(orders.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No se encontró un comprobante con esa referencia");
  var order=orders.getFirst();var lines=jdbc.queryForList("SELECT nombre,cantidad,precio FROM pago_items WHERE pago_id=? ORDER BY id",order.get("id"));
  BigDecimal subtotal=BigDecimal.ZERO;var items=new ArrayList<Map<String,Object>>();
  for(var line:lines){var price=new BigDecimal(line.get("precio").toString());int quantity=((Number)line.get("cantidad")).intValue();var amount=price.multiply(BigDecimal.valueOf(quantity));subtotal=subtotal.add(amount);items.add(Map.of("nombre",line.get("nombre"),"cantidad",quantity,"precio",price,"importe",amount));}
  BigDecimal total=new BigDecimal(order.get("total_cop").toString());
  var result=new LinkedHashMap<String,Object>();result.put("marca","TecnoPC");result.put("referencia",order.get("referencia"));result.put("cliente",order.get("cliente_nombre"));result.put("estado",order.get("estado"));result.put("fecha",order.get("creado_en"));result.put("numero_venta",order.get("numero_venta"));result.put("subtotal",subtotal);result.put("iva",total.subtract(subtotal));result.put("total",total);result.put("moneda","COP");result.put("items",items);
  result.put("preparacion",order.get("preparacion"));
  var preference=jdbc.queryForList("SELECT estado,enlace FROM preferencias_checkout WHERE referencia=?",request.referencia());
  if(!preference.isEmpty()&&"lista".equals(preference.getFirst().get("estado")))result.put("url_pago",preference.getFirst().get("enlace"));
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
 }
}
