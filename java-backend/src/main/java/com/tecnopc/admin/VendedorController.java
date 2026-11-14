package com.tecnopc.admin;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
@RestController @RequestMapping("/api/vendedor")
public class VendedorController {
 private final JdbcTemplate jdbc; private final TransactionTemplate transactions;private final CustomerNotifications notifications;private final IdempotencyService idempotency;
 public VendedorController(JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager tx,CustomerNotifications notifications,IdempotencyService idempotency){this.jdbc=jdbc;this.transactions=new TransactionTemplate(tx);this.notifications=notifications;this.idempotency=idempotency;}
 record Item(Long productoId,Integer cantidad){}
 record Venta(String clienteNombre,String clienteDocumento,List<Item> items,@Valid NotificationContact contacto){}
 @GetMapping("/productos") public List<Map<String,Object>> productos(){return jdbc.queryForList("SELECT id,nombre,categoria,stock,precio FROM productos ORDER BY nombre LIMIT 500");}
 @GetMapping("/mis-ventas") public List<Map<String,Object>> misVentas(Authentication auth){return jdbc.queryForList("SELECT numero,cliente_nombre,total,estado,creado_en FROM compras WHERE vendedor_usuario=? ORDER BY id DESC LIMIT 100",auth.getName());}
 @GetMapping("/ventas/{numero}/recibo") public ResponseEntity<?> recibo(@PathVariable String numero,Authentication auth){
  if(numero.length()>64)return ResponseEntity.badRequest().body(Map.of("mensaje","Número de venta inválido"));
  boolean admin=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));
  var rows=jdbc.queryForList("SELECT id,numero,cliente_nombre,cliente_documento,subtotal,iva,total,estado,creado_en,vendedor_usuario FROM compras WHERE numero=? AND (vendedor_usuario=? OR ?)",numero,auth.getName(),admin);
  if(rows.isEmpty())return ResponseEntity.status(404).body(Map.of("mensaje","Venta no encontrada o sin permiso para consultar su recibo"));
  var sale=rows.getFirst();var items=jdbc.queryForList("SELECT nombre_producto,cantidad,precio_unitario,subtotal FROM compra_detalles WHERE compra_id=? ORDER BY id",sale.get("id"));
  return ResponseEntity.ok(Map.of("venta",receiptFields(sale),"items",items.stream().map(this::receiptFields).toList()));
 }
 private Map<String,Object> receiptFields(Map<String,Object> row){var result=new LinkedHashMap<String,Object>();row.forEach((key,value)->result.put(key.toLowerCase(Locale.ROOT),value));return result;}
 @PostMapping({"/ventas","/v2/ventas"}) public ResponseEntity<?> venta(@Valid @RequestBody Venta venta,Authentication auth,@RequestHeader(name="Idempotency-Key",required=false) String key){
  IdempotencyService.key(key);
  if(venta==null||venta.clienteNombre()==null||venta.clienteNombre().isBlank()||venta.clienteNombre().length()>80||venta.items()==null||venta.items().isEmpty()||venta.items().size()>50)
   return ResponseEntity.badRequest().body(Map.of("mensaje","Cliente o productos inválidos"));
  var cantidades=new TreeMap<Long,Integer>();
  for(var item:venta.items()){
   if(item==null||item.productoId()==null||item.productoId()<1||item.cantidad()==null||item.cantidad()<1||item.cantidad()>1000)
    return ResponseEntity.badRequest().body(Map.of("mensaje","Producto o cantidad inválida"));
   cantidades.merge(item.productoId(),item.cantidad(),Integer::sum);
   if(cantidades.get(item.productoId())>1000) return ResponseEntity.badRequest().body(Map.of("mensaje","Cantidad excede el máximo"));
  }
  try {
   var result=idempotency.execute("venta",auth.getName(),key,venta,()->transactions.execute(status->{
    var lineas=new ArrayList<Map<String,Object>>();BigDecimal subtotal=BigDecimal.ZERO;
    for(var entry:cantidades.entrySet()){
     var rows=jdbc.queryForList("SELECT id,nombre,precio,stock FROM productos WHERE id=? FOR UPDATE",entry.getKey());
     if(rows.isEmpty())throw new IllegalArgumentException("Producto no encontrado: "+entry.getKey());
     var p=rows.get(0);int cantidad=entry.getValue();
     if(((Number)p.get("stock")).intValue()<cantidad)throw new IllegalArgumentException("Stock insuficiente: "+p.get("nombre"));
     BigDecimal precio=(BigDecimal)p.get("precio");BigDecimal linea=precio.multiply(BigDecimal.valueOf(cantidad));subtotal=subtotal.add(linea);
     lineas.add(Map.of("id",entry.getKey(),"nombre",p.get("nombre"),"cantidad",cantidad,"precio",precio,"subtotal",linea));
    }
    BigDecimal iva=subtotal.multiply(new BigDecimal("0.19")).setScale(2,RoundingMode.HALF_UP);
    BigDecimal total=subtotal.add(iva);
    String numero="VEN-"+java.time.LocalDate.now().toString().replace("-","")+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
    jdbc.update("INSERT INTO compras(numero,cliente_nombre,cliente_documento,subtotal,iva,total,vendedor_usuario) VALUES (?,?,?,?,?,?,?)",numero,venta.clienteNombre().trim(),venta.clienteDocumento(),subtotal,iva,total,auth.getName());
    Long compraId=jdbc.queryForObject("SELECT id FROM compras WHERE numero=?",Long.class,numero);
    notifications.saveContact("compras",compraId,venta.contacto());
    for(var l:lineas){
     jdbc.update("INSERT INTO compra_detalles(compra_id,producto_id,nombre_producto,cantidad,precio_unitario,subtotal) VALUES (?,?,?,?,?,?)",compraId,l.get("id"),l.get("nombre"),l.get("cantidad"),l.get("precio"),l.get("subtotal"));
     if(jdbc.update("UPDATE productos SET stock=stock-? WHERE id=? AND stock>=?",l.get("cantidad"),l.get("id"),l.get("cantidad"))!=1)throw new IllegalArgumentException("No se pudo descontar inventario");
    }
    notifications.queue("compra:"+compraId,venta.contacto(),"compra_registrada",numero);
    return Map.of("registrada",true,"numero",numero,"total",total,"compraId",compraId);
   }));
   return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }catch(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("mensaje",e.getMessage()));}
 }
}
