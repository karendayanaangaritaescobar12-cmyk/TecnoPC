package com.tecnopc.admin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:products-test;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","ADMIN_USER=product-admin","ADMIN_PASSWORD=test-admin-password"})
@AutoConfigureMockMvc
class ProductAdminTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;
 String payload(String name,String sku,int stock,int previous){return "{\"nombre\":\""+name+"\",\"categoria\":\"cpu\",\"sku\":\""+sku+"\",\"especificaciones\":\"AM5\",\"stock\":"+stock+",\"stockMinimo\":2,\"costo\":100,\"precio\":150,\"stockAnterior\":"+previous+"}";}
 @Test void createEditAndProtectStockAgainstConcurrentSales() throws Exception {
  mvc.perform(post("/api/admin/productos").with(httpBasic("product-admin","test-admin-password")).contentType("application/json").content(payload("CPU prueba","CPU-TEST",5,0))).andExpect(status().isCreated());
  long id=jdbc.queryForObject("SELECT id FROM productos WHERE sku='CPU-TEST'",Long.class);
  mvc.perform(get("/api/public/productos")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("CPU prueba")));
  mvc.perform(put("/api/admin/productos/"+id).with(httpBasic("product-admin","test-admin-password")).contentType("application/json").content(payload("CPU editada","CPU-TEST",8,5))).andExpect(status().isOk());
  jdbc.update("UPDATE productos SET stock=stock-1 WHERE id=?",id);
  mvc.perform(put("/api/admin/productos/"+id).with(httpBasic("product-admin","test-admin-password")).contentType("application/json").content(payload("CPU editada","CPU-TEST",20,8))).andExpect(status().isConflict());
  assertEquals(7,jdbc.queryForObject("SELECT stock FROM productos WHERE id=?",Integer.class,id));
 }
 @Test void validatesInputAndRejectsDuplicateSku() throws Exception {
  mvc.perform(post("/api/admin/productos").with(httpBasic("product-admin","test-admin-password")).contentType("application/json").content(payload("","BAD",-1,0))).andExpect(status().isUnprocessableEntity());
  String body=payload("SSD de prueba","UNIQUE-TEST",1,0);
  mvc.perform(post("/api/admin/productos").with(httpBasic("product-admin","test-admin-password")).contentType("application/json").content(body)).andExpect(status().isCreated());
  mvc.perform(post("/api/admin/productos").with(httpBasic("product-admin","test-admin-password")).contentType("application/json").content(body)).andExpect(status().isConflict());
 }
 @Test void onlyAdminMayManageProducts() throws Exception {
  mvc.perform(post("/api/admin/productos").contentType("application/json").content(payload("CPU","NOAUTH",1,0))).andExpect(status().isUnauthorized());
  jdbc.update("INSERT INTO usuarios(usuario,password_hash,rol,activo) VALUES (?,?,?,1)","product-seller",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode("seller-test-key"),"VENDEDOR");
  mvc.perform(get("/api/admin/productos").with(httpBasic("product-seller","seller-test-key"))).andExpect(status().isForbidden());
 }
 @Test void receiptUsesSavedAmountsAndRestrictsOtherSellers() throws Exception {
  String hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode("receipt-test-key");
  for(String user:java.util.List.of("receipt-owner","receipt-other"))jdbc.update("INSERT INTO usuarios(usuario,password_hash,rol,activo) VALUES (?,?,?,1)",user,hash,"VENDEDOR");
  jdbc.update("INSERT INTO compras(numero,cliente_nombre,subtotal,iva,total,vendedor_usuario) VALUES ('VEN-RECEIPT','Cliente prueba',100,19,119,'receipt-owner')");
  long before=jdbc.queryForObject("SELECT COUNT(*) FROM compras",Long.class);
  mvc.perform(get("/api/vendedor/ventas/VEN-RECEIPT/recibo").with(httpBasic("receipt-owner","receipt-test-key"))).andExpect(status().isOk()).andExpect(jsonPath("$.venta.total").value(119));
  mvc.perform(get("/api/vendedor/ventas/VEN-RECEIPT/recibo").with(httpBasic("receipt-other","receipt-test-key"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/vendedor/ventas/VEN-RECEIPT/recibo").with(httpBasic("product-admin","test-admin-password"))).andExpect(status().isOk());
  mvc.perform(get("/api/vendedor/ventas/VEN-RECEIPT/recibo")).andExpect(status().isUnauthorized());
  assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM compras",Long.class));
 }
 @Test void webReceiptUsesOrderSnapshotAndVerifiedStateWithoutPrivateContact() throws Exception {
  String reference="TPC-PAY-"+"b".repeat(32);
  jdbc.update("INSERT INTO productos(nombre,categoria,precio,stock) VALUES ('Producto web','pc',100,5)");
  long product=jdbc.queryForObject("SELECT id FROM productos WHERE nombre='Producto web'",Long.class);
  jdbc.update("INSERT INTO pagos_online(referencia,proveedor,estado,cliente_nombre,total_cop,contacto_json) VALUES (?,'mercadopago','pendiente','Cliente web',238,?)",reference,"{\"correo\":\"privado@example.invalid\"}");
  long order=jdbc.queryForObject("SELECT id FROM pagos_online WHERE referencia=?",Long.class,reference);
  jdbc.update("INSERT INTO pago_items(pago_id,producto_id,nombre,cantidad,precio) VALUES (?,?,'Nombre al comprar',2,100)",order,product);
  jdbc.update("UPDATE productos SET precio=999 WHERE id=?",product);
  String body="{\"referencia\":\""+reference+"\"}";
  mvc.perform(post("/api/public/pagos/recibo").contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.estado").value("pendiente")).andExpect(jsonPath("$.subtotal").value(200)).andExpect(jsonPath("$.iva").value(38)).andExpect(jsonPath("$.items[0].precio").value(100)).andExpect(jsonPath("$.items[0].nombre").value("Nombre al comprar")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("privado@example.invalid"))));
  jdbc.update("UPDATE pagos_online SET estado='revision' WHERE id=?",order);
  mvc.perform(post("/api/public/pagos/recibo").contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("revision"));
  jdbc.update("UPDATE pagos_online SET estado='aprobado' WHERE id=?",order);
  mvc.perform(post("/api/public/pagos/recibo").contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("aprobado"));
  jdbc.update("INSERT INTO compras(numero,cliente_nombre,subtotal,iva,total,preparacion) VALUES ('MP-READY-TEST','Cliente web',200,38,238,'listo')");
  long readySale=jdbc.queryForObject("SELECT id FROM compras WHERE numero='MP-READY-TEST'",Long.class);
  jdbc.update("UPDATE pagos_online SET compra_id=? WHERE id=?",readySale,order);
  mvc.perform(post("/api/public/pagos/recibo").contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.preparacion").value("listo"));
  assertEquals(5,jdbc.queryForObject("SELECT stock FROM productos WHERE id=?",Integer.class,product));
 }
 @Test void webReceiptDoesNotAcceptSequentialIdsOrUnknownReferences() throws Exception {
  mvc.perform(post("/api/public/pagos/recibo").contentType("application/json").content("{\"referencia\":\"1\"}")).andExpect(status().isUnprocessableEntity());
  mvc.perform(post("/api/public/pagos/recibo").contentType("application/json").content("{\"referencia\":\"TPC-PAY-"+"c".repeat(32)+"\"}")).andExpect(status().isNotFound());
 }
}
