package com.tecnopc.admin;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuariosPasswordTest {
    @Test void rejectsOverlongUtf8PasswordsBeforeEncodingOrWriting() {
        var jdbc=mock(JdbcTemplate.class);var encoder=mock(PasswordEncoder.class);
        var controller=new UsuariosController(jdbc,encoder);
        for(String password:new String[]{"a".repeat(73),"ñ".repeat(37)}){
            assertEquals(422,controller.crear(new UsuariosController.Crear("admin-test",password,"ADMIN",null)).getStatusCode().value());
            assertEquals(422,controller.clave(1,new UsuariosController.Clave(password)).getStatusCode().value());
        }
        verifyNoInteractions(jdbc,encoder);
    }
    @Test void acceptsExactly72Utf8BytesForPasswordReset() {
        var jdbc=mock(JdbcTemplate.class);var encoder=mock(PasswordEncoder.class);
        var controller=new UsuariosController(jdbc,encoder);String password="ñ".repeat(36);
        when(encoder.encode(password)).thenReturn("hash");
        when(jdbc.update("UPDATE usuarios SET password_hash=? WHERE id=?","hash",1L)).thenReturn(1);
        assertEquals(200,controller.clave(1,new UsuariosController.Clave(password)).getStatusCode().value());
        verify(encoder).encode(password);
    }
}
