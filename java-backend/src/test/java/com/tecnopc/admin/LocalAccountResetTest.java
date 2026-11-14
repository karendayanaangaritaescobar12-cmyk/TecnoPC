package com.tecnopc.admin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class LocalAccountResetTest {
 @TempDir Path dir;
 @Test void resetsAtomicallyOnceAndPreservesUsernames() throws Exception {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
  var jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE usuarios(id BIGINT AUTO_INCREMENT PRIMARY KEY,usuario VARCHAR(60) UNIQUE,password_hash VARCHAR(100),rol VARCHAR(20),activo BOOLEAN)");
  jdbc.update("INSERT INTO usuarios(usuario,password_hash,rol,activo) VALUES ('mi-admin','old','ADMIN',true)");
  var encoder=new BCryptPasswordEncoder(12);String hash=encoder.encode("test2510");
  Path file=dir.resolve("reset.properties");Files.writeString(file,"ADMIN="+hash+"\nVENDEDOR="+hash+"\nTECNICO="+hash+"\n");
  var reset=new LocalAccountReset(jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)),file.toString());
  reset.run(null);assertFalse(Files.exists(file));assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM usuarios",Integer.class));
  assertTrue(encoder.matches("test2510",jdbc.queryForObject("SELECT password_hash FROM usuarios WHERE usuario='mi-admin'",String.class)));
  jdbc.update("UPDATE usuarios SET password_hash='changed' WHERE rol='ADMIN'");reset.run(null);
  assertEquals("changed",jdbc.queryForObject("SELECT password_hash FROM usuarios WHERE rol='ADMIN'",String.class));
 }
}
