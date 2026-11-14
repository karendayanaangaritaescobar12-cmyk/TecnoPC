package com.tecnopc.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Distinct, catalogued scenarios. H2 only; all provider traffic is mocked. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:adversarial-roles;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "ADMIN_USER=matrix-admin","ADMIN_PASSWORD=matrix-test-password-2026","MP_WEBHOOK_SECRET=test-only",
 "EMAIL_ENABLED=false","WHATSAPP_ENABLED=false","tecnopc.notifications.initial-delay-ms=3600000",
 "logging.level.root=WARN","logging.level.org.springframework=ERROR","logging.level.org.springframework.jdbc=ERROR","logging.level.org.springframework.web=ERROR","debug=false","spring.main.banner-mode=off"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class AdversarialRolesTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired ObjectMapper json;
 @Autowired FilterChainProxy security;
 @MockitoBean MercadoPagoClient gateway;
 final List<DynamicTest> cases=new ArrayList<>();
 final List<Map<String,Object>> catalog=new ArrayList<>();
 final Path reports=Path.of("target","adversarial-results.jsonl");
 String hash;
 final String[] roles={"ADMIN","VENDEDOR","TECNICO","ANON"};
 final String[] names={"Ana","José Muñoz","电脑用户","عميل","Cliente 😀","O'Connor","<script>alert(1)</script>","'; DROP TABLE productos;--","A".repeat(80),"   "};
 interface Scenario {void run() throws Exception;}
 void add(String group,String label,Scenario scenario){
  String id=String.format(Locale.ROOT,"%s-%04d",group,cases.size()+1);
  catalog.add(Map.of("id",id,"group",group,"scenario",label));
  cases.add(DynamicTest.dynamicTest(id+" "+label,()->{
   long start=System.nanoTime(); Throwable failure=null;
   try{reset();scenario.run();}catch(Throwable e){failure=e;throw e;}
   finally{Files.writeString(reports,json.writeValueAsString(Map.of("id",id,"group",group,"result",failure==null?"PASS":"FAIL","elapsedMs",(System.nanoTime()-start)/1_000_000,"error",failure==null?"":failure.toString()))+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
  }));
 }
 String actor(String role){return switch(role){case "ADMIN"->"matrix-admin";case "VENDEDOR"->"matrix-vendor";default->"matrix-tech";};}
 MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,String role){return role.equals("ANON")?request:request.with(user(actor(role)).roles(role));}
 int request(MockHttpServletRequestBuilder request,String role,Object body) throws Exception{
  if(body!=null)request.contentType("application/json").content(json.writeValueAsBytes(body));
  return mvc.perform(as(request,role)).andReturn().getResponse().getStatus();
 }
 Map<String,Object> product(String category){var p=new LinkedHashMap<String,Object>();p.put("nombre","Producto prueba");p.put("categoria",category);p.put("sku","NEW-TEST");p.put("especificaciones","Prueba");p.put("stock",10);p.put("stockMinimo",2);p.put("costo",50);p.put("precio",100);p.put("stockAnterior",10);return p;}
 Map<String,Object> sale(String name,Object quantity){var item=new LinkedHashMap<String,Object>();item.put("productoId",1);item.put("cantidad",quantity);return new LinkedHashMap<>(Map.of("clienteNombre",name,"items",List.of(item)));}
 void reset(){
  // Sequential independent cases: reset admission history, not production limits.
  for(var chain:security.getFilterChains())for(var filter:chain.getFilters()){
   if(filter instanceof LoginAttemptFilter){((Map<?,?>)ReflectionTestUtils.getField(filter,"buckets")).clear();}
   if(filter instanceof PublicRequestFilter){((Map<?,?>)ReflectionTestUtils.getField(filter,"windows")).clear();}
  }
  db.update("DELETE FROM operaciones_idempotentes");db.update("DELETE FROM notificaciones");
  db.update("DELETE FROM compra_detalles WHERE compra_id<>1");db.update("DELETE FROM compras WHERE id<>1");
  db.update("DELETE FROM reparacion_eventos WHERE id<>1");
  db.update("DELETE FROM reparaciones WHERE id<>1");db.update("DELETE FROM solicitudes WHERE id<>1");
  db.update("DELETE FROM usuarios WHERE id NOT IN (1,2,3)");db.update("DELETE FROM productos WHERE id<>1");
  db.update("UPDATE productos SET nombre='Base',categoria='pc',sku='BASE',especificaciones='',stock=10,stock_minimo=2,costo=50,precio=100 WHERE id=1");
  for(int i=0;i<3;i++)db.update("UPDATE usuarios SET rol=?,activo=1,password_hash=? WHERE id=?",roles[i],hash,i+1);
  db.update("UPDATE reparaciones SET estado='recibido',diagnostico=NULL,trabajo_realizado=NULL,presupuesto=NULL,autorizado=0,fecha_entrega=NULL WHERE id=1");
  db.update("UPDATE solicitudes SET estado='en proceso' WHERE id=1");
 }
 @TestFactory Stream<DynamicTest> scenarios() throws Exception{
  Files.createDirectories(reports.getParent());Files.writeString(reports,"");
  hash=db.queryForObject("SELECT password_hash FROM usuarios WHERE usuario='matrix-admin'",String.class);
  db.update("INSERT INTO usuarios(id,usuario,password_hash,rol,activo) VALUES (2,'matrix-vendor',?,'VENDEDOR',1),(3,'matrix-tech',?,'TECNICO',1)",hash,hash);
  db.update("INSERT INTO productos(id,nombre,categoria,sku,precio,stock) VALUES(1,'Base','pc','BASE',100,10)");
  db.update("INSERT INTO solicitudes(id,tipo,nombre,telefono,detalle,estado) VALUES(1,'servicio','CLIENTE-RESERVADO-PRUEBA','3001234567','Prueba','en proceso')");
  db.update("INSERT INTO reparaciones(id,solicitud_id,equipo,tecnico_usuario,codigo_ingreso) VALUES(1,1,'Equipo prueba','matrix-tech','TPC-2026-ABCDEFGH2345')");
  db.update("INSERT INTO reparacion_eventos(id,reparacion_id,usuario,evento,detalle) VALUES(1,1,'matrix-tech','recibido','CLIENTE-RESERVADO-PRUEBA')");
  db.update("INSERT INTO compras(id,numero,cliente_nombre,subtotal,iva,total,vendedor_usuario) VALUES(1,'VEN-MATRIX-BASE','CLIENTE-RESERVADO-PRUEBA',100,19,119,'matrix-vendor')");
  permissions();products();sales();technician();replays();identifiers();invalidUsers();realAuthentication();tampering();mutations();
  Files.writeString(Path.of("target","adversarial-catalog.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(catalog));
  assertTrue(cases.size()>=1500,"Unique catalog must contain at least 1500 scenarios");
  return cases.stream();
 }
 void permissions(){
  String[] endpoints={"/api/admin/resumen","/api/admin/inventario","/api/admin/alertas","/api/admin/compras","/api/admin/empleados","/api/admin/productos","/api/admin/usuarios","/api/admin/notificaciones","/api/admin/diagnostico","/api/vendedor/productos","/api/vendedor/mis-ventas","/api/vendedor/pedidos","/api/vendedor/ventas/VEN-MATRIX-BASE/recibo","/api/tecnico/solicitudes","/api/tecnico/reparaciones","/api/tecnico/reparaciones/1/eventos"};
  for(String role:roles)for(int e=0;e<endpoints.length;e++)for(int context=0;context<3;context++){
   String path=endpoints[e];int c=context;boolean allowed=role.equals("ADMIN")||(e>=9&&e<=12&&role.equals("VENDEDOR"))||(e>=13&&role.equals("TECNICO"));
   int expected=role.equals("ANON")?401:allowed?200:403;
   add("RBAC",role+" GET "+path+" headers="+c+" expected="+expected,()->{
    var req=get(path);if(c==1)req.header("Origin","https://untrusted.example");if(c==2)req.header("Sec-Fetch-Site","cross-site");
    var result=mvc.perform(as(req,role)).andReturn().getResponse();assertEquals(expected,result.getStatus(),result.getContentAsString());
    if(!allowed)assertFalse(result.getContentAsString().contains("CLIENTE-RESERVADO-PRUEBA"));
   });
  }
 }
 void products(){
  String[] cats={"pc","laptop","cpu","gpu","ram","ssd","mb","psu","case"};
  String[] fields={"nombre","nombre","nombre","nombre","nombre","nombre","sku","sku","especificaciones","especificaciones","stock","stock","stock","stock","stockMinimo","stockMinimo","costo","costo","costo","precio","precio","precio","precio","precio"};
  Object[] values={"Normal","N".repeat(80),"N".repeat(81),null,"","   ","S".repeat(30),"S".repeat(31),"E".repeat(160),"E".repeat(161),0,-1,1000000,1000001,-1,1000001,0,new BigDecimal("-0.01"),new BigDecimal("1.001"),0,new BigDecimal("0.01"),new BigDecimal("10000000000.00"),new BigDecimal("1.001"),null};
  Set<Integer> valid=Set.of(0,1,6,8,10,12,16,20);
  for(String cat:cats)for(int v=0;v<values.length;v++)for(boolean edit:new boolean[]{false,true}){
   int variant=v,expected=valid.contains(v)?(edit?200:201):422;
   add("PRODUCT",cat+" variant="+v+" edit="+edit+" expected="+expected,()->{
    var body=product(cat);body.put(fields[variant],values[variant]);
    int before=db.queryForObject("SELECT COUNT(*) FROM productos",Integer.class);
    assertEquals(expected,request(edit?put("/api/admin/productos/1"):post("/api/admin/productos"),"ADMIN",body));
    assertEquals(before+(!edit&&expected==201?1:0),db.queryForObject("SELECT COUNT(*) FROM productos",Integer.class));
    if(expected==422)assertEquals(10,db.queryForObject("SELECT stock FROM productos WHERE id=1",Integer.class));
   });
  }
 }
 void sales(){
  Object[] quantities={1,2,10,0,-1,101,1000,1001,null,new BigDecimal("1.5")};
  for(String role:List.of("ADMIN","VENDEDOR"))for(int q=0;q<10;q++)for(int n=0;n<10;n++){
   Object qty=quantities[q];String name=names[n];int expected=q==9?422:n==9||q>=3?400:201;
   add("SALE",role+" quantityVariant="+q+" nameVariant="+n+" expected="+expected,()->{
    assertEquals(expected,request(post("/api/vendedor/v2/ventas").header("Idempotency-Key",UUID.randomUUID()),role,sale(name,qty)));
    verifySale(role,expected==201?((Number)qty).intValue():0);
   });
  }
 }
 void verifySale(String role,int qty){
  assertEquals(qty==0?1:2,db.queryForObject("SELECT COUNT(*) FROM compras",Integer.class));
  assertEquals(10-qty,db.queryForObject("SELECT stock FROM productos WHERE id=1",Integer.class));
  if(qty>0){assertEquals(0,new BigDecimal(119*qty).compareTo(db.queryForObject("SELECT total FROM compras WHERE id<>1",BigDecimal.class)));assertEquals(actor(role),db.queryForObject("SELECT vendedor_usuario FROM compras WHERE id<>1",String.class));}
 }
 void technician(){
  String[] states={"recibido","diagnostico","esperando_autorizacion","en_reparacion","listo","entregado","cancelado"};
  var edges=Map.of("recibido",Set.of("diagnostico","cancelado"),"diagnostico",Set.of("esperando_autorizacion","cancelado"),"esperando_autorizacion",Set.of("en_reparacion","cancelado"),"en_reparacion",Set.of("listo","cancelado"),"listo",Set.of("entregado"),"entregado",Set.<String>of(),"cancelado",Set.<String>of());
  for(String from:states)for(String to:states)for(int details=0;details<3;details++){
   int d=details;boolean edge=from.equals(to)||edges.get(from).contains(to);
   int expected=!edge?409:d==2&&(to.equals("esperando_autorizacion")||to.equals("en_reparacion")||to.equals("listo"))?422:to.equals("en_reparacion")&&d==1?409:200;
   add("TECH",from+" -> "+to+" details="+d+" expected="+expected,()->{
    db.update("UPDATE reparaciones SET estado=? WHERE id=1",from);
    var body=new LinkedHashMap<String,Object>();body.put("estado",to);
    if(d<2){body.put("diagnostico","Diagnóstico prueba");body.put("trabajoRealizado","Trabajo prueba");body.put("presupuesto",100);body.put("autorizado",d==0);}
    assertEquals(expected,request(patch("/api/tecnico/reparaciones/1"),"TECNICO",body));
    assertEquals(expected==200?to:from,db.queryForObject("SELECT estado FROM reparaciones WHERE id=1",String.class));
    assertEquals(expected==200?2:1,db.queryForObject("SELECT COUNT(*) FROM reparacion_eventos",Integer.class));
   });
  }
 }
 void replays(){
  for(String role:List.of("ADMIN","VENDEDOR"))for(int qty=1;qty<=3;qty++)for(int name=0;name<10;name++)for(boolean changed:new boolean[]{false,true}){
   int quantity=qty;String client=name==9?"Cliente\ncon salto":names[name];
   add("REPLAY",role+" qty="+qty+" name="+name+" changed="+changed,()->{
    String key=UUID.randomUUID().toString();var body=sale(client,quantity);
    var first=mvc.perform(as(post("/api/vendedor/v2/ventas").header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsBytes(body)),role)).andReturn().getResponse();
    assertEquals(201,first.getStatus());
    var second=mvc.perform(as(post("/api/vendedor/v2/ventas").header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsBytes(changed?sale(client,quantity+1):body)),role)).andReturn().getResponse();
    assertEquals(changed?409:201,second.getStatus());if(!changed)assertEquals(json.readTree(first.getContentAsString()),json.readTree(second.getContentAsString()));
    verifySale(role,quantity);assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM operaciones_idempotentes",Integer.class));
   });
  }
 }
 void identifiers(){
  // Literal single path segments; no ambiguous decoding in this matrix.
  List<String> ids=new ArrayList<>(List.of("1","0","-1","9223372036854775807","-9223372036854775808","9223372036854775808","1.5","null","undefined","NaN","VEN-MATRIX-BASE","'","1'OR'1'='1","<script>","用户","😀","a b","a+b","a=b","a&b","a;b","a%b","a#b","a?b","a:b","a@b","a,b","a(b)","a[b]","a{b}","a|b","a~b","a!b","a$b","a*b","a_b","a-b","a.b","0001","+1","-0","1e3","0x01","TRUE","false","A".repeat(64),"B".repeat(65),"C".repeat(200),"é","550e8400-e29b-41d4-a716-446655440000"));
  assertEquals(50,ids.size());
  for(String role:roles)for(int i=0;i<ids.size();i++)for(boolean tech:new boolean[]{false,true}){
   String id=ids.get(i);boolean allowed=role.equals("ADMIN")||role.equals(tech?"TECNICO":"VENDEDOR");
   int domain;if(tech){try{org.springframework.util.NumberUtils.parseNumber(id,Long.class);domain=200;}catch(IllegalArgumentException ex){domain=400;}}else domain=id.equals("VEN-MATRIX-BASE")?200:id.length()>64?400:404;
   int expected=role.equals("ANON")?401:allowed?domain:403;
   add("IDENTIFIER",role+" idVariant="+i+" tech="+tech+" expected="+expected,()->{
    var req=get(tech?"/api/tecnico/reparaciones/{id}/eventos":"/api/vendedor/ventas/{id}/recibo",id);
    var response=mvc.perform(as(req,role)).andReturn().getResponse();
    // StrictHttpFirewall rejects semicolon/encoded percent before authentication.
    boolean firewall=id.contains(";")||id.contains("%");
    assertEquals(firewall?400:expected,response.getStatus(),response.getContentAsString());
    if(!allowed||firewall)assertFalse(response.getContentAsString().contains("CLIENTE-RESERVADO-PRUEBA"));
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM compras",Integer.class));
    assertEquals(10,db.queryForObject("SELECT stock FROM productos WHERE id=1",Integer.class));
   });
  }
 }
 void invalidUsers(){
  String[] fields={"rol","rol","rol","rol","rol","rol","usuario","usuario","usuario","usuario","usuario","usuario","password","password","password","password"};
  Object[] values={null,"","admin","SUPERADMIN"," ADMIN",123,null,"","ab","U".repeat(61),"';DROP TABLE usuarios;--","usuarioñ",null,"","1234567","ñ".repeat(37)};
  for(String role:roles)for(int i=0;i<values.length;i++){
   int v=i,expected=role.equals("ADMIN")?422:role.equals("ANON")?401:403;
   add("USER",role+" invalidVariant="+i+" expected="+expected,()->{
    var body=new LinkedHashMap<String,Object>(Map.of("usuario","new-user","password","test-only-password","rol","VENDEDOR"));body.put(fields[v],values[v]);
    assertEquals(expected,request(post("/api/admin/usuarios"),role,body));assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM usuarios",Integer.class));
   });
  }
 }
 void realAuthentication(){
  for(String role:List.of("ADMIN","VENDEDOR","TECNICO"))for(int mode=0;mode<4;mode++){
   int m=mode;String path=role.equals("ADMIN")?"/api/admin/productos":role.equals("VENDEDOR")?"/api/vendedor/productos":"/api/tecnico/reparaciones";
   add("AUTH",role+" credentialsMode="+mode+" expected="+(m==0?200:401),()->{
    if(m==2)db.update("UPDATE usuarios SET activo=0 WHERE usuario=?",actor(role));
    var req=get(path);if(m!=3)req.with(httpBasic(actor(role),m==1?"incorrect-password":"matrix-test-password-2026"));
    assertEquals(m==0?200:401,mvc.perform(req).andReturn().getResponse().getStatus());
   });
  }
 }
 void tampering(){
  String[] fields={"precio","total","iva","stock","vendedor_usuario","rol","compraId","numero","registrada","items"};
  Object[] values={0,-999,0,9999,"other-seller","ADMIN",1,"VEN-MATRIX-BASE",true,List.of(Map.of("productoId",1,"cantidad",1,"precio",0))};
  for(String role:List.of("ADMIN","VENDEDOR"))for(int i=0;i<fields.length;i++){
   int v=i;add("TAMPER",role+" forgedField="+fields[i],()->{
    var body=sale("Cliente prueba",1);body.put(fields[v],values[v]);
    assertEquals(201,request(post("/api/vendedor/v2/ventas").header("Idempotency-Key",UUID.randomUUID()),role,body));verifySale(role,1);
   });
  }
 }
 void mutations(){
  for(String role:List.of("ADMIN","VENDEDOR","TECNICO"))for(int route=0;route<8;route++)for(int context=0;context<4;context++){
   int r=route,c=context;
   boolean permitted=role.equals("ADMIN")||r==5&&role.equals("VENDEDOR")||r>=6&&role.equals("TECNICO");
   int expected=c==3?415:c>0?403:!permitted?403:(r==0||r==2||r==5||r==6)?201:200;
   add("MUTATION",role+" route="+r+" headers="+c+" expected="+expected,()->{
    MockHttpServletRequestBuilder req;Object body;
    switch(r){
     case 0: req=post("/api/admin/productos");body=product("pc");break;
     case 1: req=put("/api/admin/productos/1");body=product("pc");break;
     case 2: req=post("/api/admin/usuarios");body=Map.of("usuario","new-user","password","test-only-password","rol","VENDEDOR");break;
     case 3: req=patch("/api/admin/usuarios/2");body=Map.of("rol","TECNICO","activo",true);break;
     case 4: req=put("/api/admin/usuarios/2/clave");body=Map.of("password","changed-test-password");break;
     case 5: req=post("/api/vendedor/v2/ventas").header("Idempotency-Key",UUID.randomUUID());body=sale("Cliente prueba",1);break;
     case 6:
      db.update("INSERT INTO solicitudes(id,tipo,nombre,telefono,detalle) VALUES(2,'servicio','Prueba','3001234567','Prueba')");
      req=post("/api/tecnico/reparaciones");body=Map.of("solicitudId",2,"equipo","Equipo prueba");break;
     default: req=patch("/api/tecnico/reparaciones/1");body=Map.of("estado","diagnostico");
    }
    if(c==1)req.header("Origin","https://untrusted.example");if(c==2)req.header("Sec-Fetch-Site","cross-site");
    req.contentType(c==3?"application/x-www-form-urlencoded":"application/json").content(json.writeValueAsBytes(body));
    assertEquals(expected,mvc.perform(as(req,role)).andReturn().getResponse().getStatus());
    if(expected>=400){assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM compras",Integer.class));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM productos",Integer.class));assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM usuarios",Integer.class));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM reparaciones",Integer.class));assertEquals("recibido",db.queryForObject("SELECT estado FROM reparaciones WHERE id=1",String.class));assertEquals("VENDEDOR",db.queryForObject("SELECT rol FROM usuarios WHERE id=2",String.class));}
   });
  }
 }
}
