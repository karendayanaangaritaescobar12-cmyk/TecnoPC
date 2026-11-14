package com.tecnopc.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/productos")
public class ProductAdminController {
 private final JdbcTemplate jdbc;
 public ProductAdminController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 record Product(@NotBlank @Size(max=80) String nombre,
   @NotNull @Pattern(regexp="pc|laptop|cpu|gpu|ram|ssd|mb|psu|case") String categoria,
   @Size(max=30) String sku,@Size(max=160) String especificaciones,
   @NotNull @Min(0) @Max(1000000) Integer stock,
   @NotNull @Min(0) @Max(1000000) Integer stockMinimo,
   @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal costo,
   @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal precio,
   @Min(0) Integer stockAnterior){}
 @GetMapping public List<Map<String,Object>> list(){return jdbc.queryForList("SELECT id,nombre,categoria,sku,especificaciones,stock,stock_minimo,costo,precio FROM productos ORDER BY nombre LIMIT 2000");}
 @PostMapping public ResponseEntity<?> create(@Valid @RequestBody Product p){
  try{jdbc.update("INSERT INTO productos(nombre,categoria,sku,especificaciones,stock,stock_minimo,costo,precio) VALUES (?,?,?,?,?,?,?,?)",p.nombre().trim(),p.categoria(),sku(p.sku()),p.especificaciones(),p.stock(),p.stockMinimo(),p.costo(),p.precio());
   return ResponseEntity.status(201).body(Map.of("mensaje","Producto creado"));
  }catch(DuplicateKeyException ex){return error("El SKU ya existe",409);}
 }
 @PutMapping("/{id}") public ResponseEntity<?> edit(@PathVariable long id,@Valid @RequestBody Product p){
  if(p.stockAnterior()==null)return error("Actualiza el inventario antes de editar",422);
  try{int n=jdbc.update("UPDATE productos SET nombre=?,categoria=?,sku=?,especificaciones=?,stock=?,stock_minimo=?,costo=?,precio=? WHERE id=? AND stock=?",p.nombre().trim(),p.categoria(),sku(p.sku()),p.especificaciones(),p.stock(),p.stockMinimo(),p.costo(),p.precio(),id,p.stockAnterior());
   return n==1?ResponseEntity.ok(Map.of("mensaje","Producto actualizado")):error("El producto cambió o ya no existe. Actualiza y vuelve a editar para conservar las ventas recientes.",409);
  }catch(DuplicateKeyException ex){return error("El SKU ya existe",409);}
 }
 private String sku(String s){return s==null||s.isBlank()?null:s.trim();}
 private ResponseEntity<?> error(String text,int code){return ResponseEntity.status(code).body(Map.of("mensaje",text));}
}
