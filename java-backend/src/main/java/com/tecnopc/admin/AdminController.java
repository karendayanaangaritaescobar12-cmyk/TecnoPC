package com.tecnopc.admin;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/api/admin")
public class AdminController {
 private final JdbcTemplate jdbc;
 public AdminController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping("/resumen") public Map<String,Object> resumen(){
  return Map.of("productos",jdbc.queryForObject("SELECT COUNT(*) FROM productos",Integer.class),
  "stockBajo",jdbc.queryForObject("SELECT COUNT(*) FROM productos WHERE stock <= stock_minimo",Integer.class),
  "compras",jdbc.queryForObject("SELECT COUNT(*) FROM compras",Integer.class),
  "empleadosActivos",jdbc.queryForObject("SELECT COUNT(*) FROM empleados WHERE estado='activo'",Integer.class));
 }
 @GetMapping("/inventario") public List<Map<String,Object>> inventario(){return jdbc.queryForList("SELECT id,nombre,categoria,stock,stock_minimo,precio FROM productos ORDER BY nombre LIMIT 500");}
 @GetMapping("/alertas") public List<Map<String,Object>> alertas(){return jdbc.queryForList("SELECT id,nombre,stock,stock_minimo FROM productos WHERE stock <= stock_minimo ORDER BY stock LIMIT 200");}
 @GetMapping("/compras") public List<Map<String,Object>> compras(){return jdbc.queryForList("SELECT numero,cliente_nombre,total,estado,creado_en FROM compras ORDER BY id DESC LIMIT 100");}
 @GetMapping("/empleados") public List<Map<String,Object>> empleados(){return jdbc.queryForList("SELECT id,nombres,apellidos,cargo,estado FROM empleados ORDER BY apellidos LIMIT 200");}
}
