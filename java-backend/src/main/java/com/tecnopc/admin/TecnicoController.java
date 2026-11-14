package com.tecnopc.admin;

import java.math.BigDecimal;
import java.util.*;
import java.security.SecureRandom;
import java.time.Year;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tecnico")
public class TecnicoController {
 private static final SecureRandom RNG = new SecureRandom();
 private static final String ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
 private static String codigoIngreso(){StringBuilder b=new StringBuilder("TPC-").append(Year.now().getValue()).append('-');for(int i=0;i<12;i++)b.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));return b.toString();}
 private final JdbcTemplate jdbc;
 private final CustomerNotifications notifications;
 public TecnicoController(JdbcTemplate jdbc,CustomerNotifications notifications){this.jdbc=jdbc;this.notifications=notifications;}
 record Recepcion(long solicitudId,String equipo,String marcaModelo,String serie,String accesorios){}
 record Actualizacion(String estado,String diagnostico,String trabajoRealizado,BigDecimal presupuesto,Boolean autorizado){}
 private ResponseEntity<?> error(String msg,int code){return ResponseEntity.status(code).body(Map.of("mensaje",msg));}
 @GetMapping("/solicitudes") public List<Map<String,Object>> solicitudes(){
  return jdbc.queryForList("SELECT s.id,s.nombre,s.telefono,s.detalle,s.estado,s.creado_en FROM solicitudes s LEFT JOIN reparaciones r ON r.solicitud_id=s.id WHERE s.tipo='servicio' AND r.id IS NULL ORDER BY s.id DESC LIMIT 100");
 }
 @GetMapping("/reparaciones") public List<Map<String,Object>> reparaciones(){
  return jdbc.queryForList("SELECT r.id,r.codigo_ingreso,r.solicitud_id,r.equipo,r.marca_modelo,r.serie,r.accesorios,r.diagnostico,r.trabajo_realizado,r.presupuesto,r.autorizado,r.estado,r.tecnico_usuario,r.fecha_entrega,r.creado_en,s.nombre AS cliente,s.telefono FROM reparaciones r JOIN solicitudes s ON s.id=r.solicitud_id ORDER BY r.id DESC LIMIT 200");
 }
 @GetMapping("/reparaciones/{id}/eventos") public List<Map<String,Object>> eventos(@PathVariable long id){
  return jdbc.queryForList("SELECT usuario,evento,detalle,creado_en FROM reparacion_eventos WHERE reparacion_id=? ORDER BY id",id);
 }
 @PostMapping("/reparaciones") @Transactional public ResponseEntity<?> recibir(@RequestBody Recepcion d,Authentication auth){
  if(d==null||d.solicitudId()<1||d.equipo()==null||d.equipo().isBlank()||d.equipo().length()>120)return error("Solicitud y equipo obligatorios",422);
  var rows=jdbc.queryForList("SELECT id,contacto_json FROM solicitudes WHERE id=? AND tipo='servicio'",d.solicitudId());
  if(rows.isEmpty())return error("Solicitud de servicio no encontrada",404);
  Integer existe=jdbc.queryForObject("SELECT COUNT(*) FROM reparaciones WHERE solicitud_id=?",Integer.class,d.solicitudId());
  if(existe!=null&&existe>0)return error("Esta solicitud ya fue recibida",409);
  String codigo=null;boolean creado=false;
  for(int intento=0;intento<5&&!creado;intento++){
   codigo=codigoIngreso();
   try {jdbc.update("INSERT INTO reparaciones(solicitud_id,equipo,marca_modelo,serie,accesorios,tecnico_usuario,codigo_ingreso) VALUES (?,?,?,?,?,?,?)",d.solicitudId(),d.equipo().trim(),d.marcaModelo(),d.serie(),d.accesorios(),auth.getName(),codigo);creado=true;}
   catch(DuplicateKeyException ex){if(intento==4)throw ex;}
  }
  Long id=jdbc.queryForObject("SELECT id FROM reparaciones WHERE solicitud_id=?",Long.class,d.solicitudId());
  jdbc.update("INSERT INTO reparacion_eventos(reparacion_id,usuario,evento,detalle) VALUES (?,?,?,?)",id,auth.getName(),"recibido","Equipo ingresado a taller");
  notifications.queue("recepcion:"+id,notifications.readContact(rows.get(0).get("contacto_json")),"servicio_recibido",codigo);
  jdbc.update("UPDATE solicitudes SET estado='en proceso' WHERE id=?",d.solicitudId());
  return ResponseEntity.status(201).body(Map.of("id",id,"codigoIngreso",codigo,"mensaje","Equipo recibido correctamente"));
 }
 @PatchMapping("/reparaciones/{id}") @Transactional public ResponseEntity<?> actualizar(@PathVariable long id,@RequestBody Actualizacion d,Authentication auth){
  if(d==null||d.estado()==null)return error("Estado obligatorio",422);
  var estados=List.of("recibido","diagnostico","esperando_autorizacion","en_reparacion","listo","entregado","cancelado");
  if(!estados.contains(d.estado()))return error("Estado inválido",422);
  var rows=jdbc.queryForList("SELECT estado,autorizado,diagnostico,trabajo_realizado,presupuesto FROM reparaciones WHERE id=? FOR UPDATE",id);
  if(rows.isEmpty())return error("Reparación no encontrada",404);
  var actual=rows.get(0);String anterior=(String)actual.get("estado");
  var transiciones=Map.of(
   "recibido",Set.of("diagnostico","cancelado"),
   "diagnostico",Set.of("esperando_autorizacion","cancelado"),
   "esperando_autorizacion",Set.of("en_reparacion","cancelado"),
   "en_reparacion",Set.of("listo","cancelado"),
   "listo",Set.of("entregado"),"entregado",Set.<String>of(),"cancelado",Set.<String>of());
  if(!anterior.equals(d.estado())&&!transiciones.get(anterior).contains(d.estado()))return error("Transición de estado no permitida: "+anterior+" → "+d.estado(),409);
  boolean autorizado=d.autorizado()==null?(Boolean)actual.get("autorizado"):d.autorizado();
  String diagnostico=d.diagnostico()==null?(String)actual.get("diagnostico"):d.diagnostico().trim();
  String trabajo=d.trabajoRealizado()==null?(String)actual.get("trabajo_realizado"):d.trabajoRealizado().trim();
  BigDecimal presupuesto=d.presupuesto()==null?(BigDecimal)actual.get("presupuesto"):d.presupuesto();
  if(presupuesto!=null&&presupuesto.signum()<0)return error("Presupuesto inválido",422);
  if((d.estado().equals("esperando_autorizacion")||d.estado().equals("en_reparacion"))&&(diagnostico==null||diagnostico.isBlank()||presupuesto==null))return error("Primero registra diagnóstico y presupuesto",422);
  if(d.estado().equals("en_reparacion")&&!autorizado)return error("Se requiere autorización registrada del cliente",409);
  if(d.estado().equals("listo")&&(trabajo==null||trabajo.isBlank()))return error("Describe el trabajo realizado",422);
  jdbc.update("UPDATE reparaciones SET estado=?,diagnostico=?,trabajo_realizado=?,presupuesto=?,autorizado=?,fecha_entrega=CASE WHEN ?='entregado' THEN CURRENT_TIMESTAMP ELSE fecha_entrega END WHERE id=?",d.estado(),diagnostico,trabajo,presupuesto,autorizado,d.estado(),id);
  jdbc.update("INSERT INTO reparacion_eventos(reparacion_id,usuario,evento,detalle) VALUES (?,?,?,?)",id,auth.getName(),d.estado(),"Actualización de orden; autorización registrada: "+autorizado);
  String solicitudEstado=switch(d.estado()){case "listo"->"listo";case "entregado","cancelado"->"cerrado";default->"en proceso";};
  jdbc.update("UPDATE solicitudes SET estado=? WHERE id=(SELECT solicitud_id FROM reparaciones WHERE id=?)",solicitudEstado,id);
  if(!anterior.equals("listo")&&d.estado().equals("listo")){
   var contactRows=jdbc.queryForList("SELECT s.contacto_json,r.codigo_ingreso FROM reparaciones r JOIN solicitudes s ON s.id=r.solicitud_id WHERE r.id=?",id);
   var contact=contactRows.get(0);
   notifications.queue("servicio-listo:"+id,notifications.readContact(contact.get("contacto_json")),"servicio_listo",contact.get("codigo_ingreso").toString());
  }
  return ResponseEntity.ok(Map.of("mensaje","Reparación actualizada","estado",d.estado()));
 }
}
