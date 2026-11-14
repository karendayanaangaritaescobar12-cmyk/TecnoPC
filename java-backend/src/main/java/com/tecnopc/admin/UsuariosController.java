package com.tecnopc.admin;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DuplicateKeyException;
@RestController @RequestMapping("/api/admin/usuarios")
public class UsuariosController {
 private final JdbcTemplate jdbc; private final PasswordEncoder encoder;
 public UsuariosController(JdbcTemplate jdbc,PasswordEncoder encoder){this.jdbc=jdbc;this.encoder=encoder;}
 record Crear(String usuario,String password,String rol,Long empleadoId){}
 record Cambiar(String rol,Boolean activo){}
 record Clave(String password){}
 @GetMapping public List<Map<String,Object>> listar(){return jdbc.queryForList("SELECT id,empleado_id,usuario,rol,activo,creado_en FROM usuarios ORDER BY id DESC");}
 @PostMapping public ResponseEntity<?> crear(@RequestBody Crear d){
  if(d==null||d.usuario()==null||!d.usuario().matches("[a-zA-Z0-9._-]{3,60}")||!claveValida(d.password())||!rolValido(d.rol()))return error("Datos inválidos: usuario de 3-60 caracteres, clave de 8+ caracteres y hasta 72 bytes, y rol permitido",422);
  try {jdbc.update("INSERT INTO usuarios(usuario,password_hash,rol,empleado_id,activo) VALUES (?,?,?,?,1)",d.usuario(),encoder.encode(d.password()),d.rol(),d.empleadoId());return ResponseEntity.status(201).body(Map.of("mensaje","Cuenta creada"));}
  catch(DuplicateKeyException e){return error("Usuario o empleado ya vinculado",409);}
  catch(Exception e){return error("No fue posible crear la cuenta. Verifica el empleado indicado.",422);}
 }
 @PatchMapping("/{id}") public ResponseEntity<?> cambiar(@PathVariable long id,@RequestBody Cambiar d,Authentication auth){
  if(d==null||(d.rol()==null&&d.activo()==null)||(d.rol()!=null&&!rolValido(d.rol())))return error("Cambios inválidos",422);
  var rows=jdbc.queryForList("SELECT usuario,rol,activo FROM usuarios WHERE id=?",id);
  if(rows.isEmpty())return error("Cuenta no encontrada",404);
  var actual=rows.get(0);
  String rol=d.rol()==null?(String)actual.get("rol"):d.rol();
  boolean activo=d.activo()==null?(Boolean)actual.get("activo"):d.activo();
  if(auth.getName().equals(actual.get("usuario"))&&(!activo||!rol.equals("ADMIN")))return error("No puedes desactivar o quitarte tu propio rol administrador",409);
  if("ADMIN".equals(actual.get("rol"))&&(!activo||!rol.equals("ADMIN"))){
   Integer otros=jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE rol='ADMIN' AND activo=1 AND id<>?",Integer.class,id);
   if(otros==null||otros<1)return error("Debe permanecer al menos un administrador activo",409);
  }
  jdbc.update("UPDATE usuarios SET rol=?,activo=? WHERE id=?",rol,activo,id);
  return ResponseEntity.ok(Map.of("mensaje","Permisos actualizados"));
 }
 @PutMapping("/{id}/clave") public ResponseEntity<?> clave(@PathVariable long id,@RequestBody Clave d){
  if(d==null||!claveValida(d.password()))return error("La contraseña debe tener al menos 8 caracteres y hasta 72 bytes UTF-8",422);
  int n=jdbc.update("UPDATE usuarios SET password_hash=? WHERE id=?",encoder.encode(d.password()),id);
  return n==0?error("Cuenta no encontrada",404):ResponseEntity.ok(Map.of("mensaje","Contraseña actualizada"));
 }
 private boolean rolValido(String r){return r!=null&&Set.of("ADMIN","VENDEDOR","TECNICO").contains(r);}
 private boolean claveValida(String password){return password!=null&&password.length()>=8&&password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72;}
 private ResponseEntity<?> error(String m,int status){return ResponseEntity.status(status).body(Map.of("mensaje",m));}
}
