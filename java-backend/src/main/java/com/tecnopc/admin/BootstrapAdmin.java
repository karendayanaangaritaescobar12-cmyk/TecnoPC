package com.tecnopc.admin;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
@Component
@org.springframework.core.annotation.Order(0)
public class BootstrapAdmin implements ApplicationRunner {
 private final JdbcTemplate jdbc; private final PasswordEncoder encoder;private final String initialUser,initialPassword;
 public BootstrapAdmin(JdbcTemplate jdbc,PasswordEncoder encoder,@Value("${ADMIN_USER:}") String user,@Value("${ADMIN_PASSWORD:}") String password){this.jdbc=jdbc;this.encoder=encoder;this.initialUser=user;this.initialPassword=password;}
 @Override public void run(ApplicationArguments args){
  Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE rol='ADMIN'",Integer.class);
  if(count!=null && count>0)return;
  String user=initialUser,pass=initialPassword;
  if(user==null||!user.matches("[a-zA-Z0-9._-]{3,60}")||pass==null||pass.length()<8||pass.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
   throw new IllegalStateException("Primer inicio: define ADMIN_USER y ADMIN_PASSWORD (8+ caracteres)");
  jdbc.update("INSERT INTO usuarios(usuario,password_hash,rol,activo) VALUES (?,?,'ADMIN',1)",user,encoder.encode(pass));
 }
}
