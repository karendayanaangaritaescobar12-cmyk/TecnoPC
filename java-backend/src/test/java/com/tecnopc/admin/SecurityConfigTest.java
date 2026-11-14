package com.tecnopc.admin;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AdminController.class,RuntimeDiagnostics.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean javax.sql.DataSource datasource;

    @Test void storeCspOnlyAllowsStylesFromThisOrigin() throws Exception {
        mvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Security-Policy",
                org.hamcrest.Matchers.containsString("style-src 'self';")))
            .andExpect(header().string("Content-Security-Policy",
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("'unsafe-inline'"))));
    }

    @Test void httpsStoreResponseIncludesHstsForThisHostOnly() throws Exception {
        mvc.perform(get("/index.html").secure(true))
            .andExpect(status().isOk())
            .andExpect(header().string("Strict-Transport-Security", "max-age=31536000"));
    }

    @Test void httpsAuthenticationFailureAlsoIncludesHsts() throws Exception {
        mvc.perform(get("/api/admin/empleados").secure(true))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("Strict-Transport-Security", "max-age=31536000"));
    }

    @Test void plainHttpDoesNotEmitHstsEvenWithClientSuppliedHttpsHeader() throws Exception {
        mvc.perform(get("/index.html").secure(false).header("X-Forwarded-Proto", "https"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test void anonymousCannotReadPrivateData() throws Exception {
        mvc.perform(get("/api/admin/empleados")).andExpect(status().isUnauthorized());
    }
    @Test void sellerCannotReadAdminData() throws Exception {
        mvc.perform(get("/api/admin/empleados").with(user("seller").roles("VENDEDOR")))
            .andExpect(status().isForbidden());
    }
    @Test void technicianCannotReadAdminData() throws Exception {
        mvc.perform(get("/api/admin/empleados").with(user("tech").roles("TECNICO")))
            .andExpect(status().isForbidden());
    }
    @Test void adminCanReadInventory() throws Exception {
        mvc.perform(get("/api/admin/inventario").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }
    @Test void unlistedRoutesAreDenied() throws Exception {
        mvc.perform(get("/private").with(user("admin").roles("ADMIN")))
            .andExpect(status().isForbidden());
    }
    @Test void browserMutationIsRejected() throws Exception {
        mvc.perform(post("/api/admin/usuarios").with(user("admin").roles("ADMIN"))
            .header("Origin", "https://attacker.example").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }
    @Test void browserFetchMutationIsRejected() throws Exception {
        mvc.perform(post("/api/admin/usuarios").with(user("admin").roles("ADMIN"))
            .header("Sec-Fetch-Site", "cross-site").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }
    @Test void formMutationIsRejected() throws Exception {
        mvc.perform(post("/api/admin/usuarios").with(user("admin").roles("ADMIN"))
            .contentType("application/x-www-form-urlencoded").content("usuario=test"))
            .andExpect(status().isUnsupportedMediaType());
    }
    @Test void bcryptCredentialsAreAcceptedWithoutSession() throws Exception {
        when(jdbc.queryForList("SELECT usuario,password_hash,rol,activo FROM usuarios WHERE usuario=?", "admin"))
            .thenReturn(List.of(Map.of("usuario", "admin", "password_hash", encoder.encode("correct-password"),
                                      "rol", "ADMIN", "activo", true)));
        mvc.perform(get("/api/admin/inventario").with(httpBasic("admin", "correct-password")))
            .andExpect(status().isOk()).andExpect(cookie().doesNotExist("JSESSIONID"));
        mvc.perform(get("/api/admin/inventario")).andExpect(status().isUnauthorized());
    }
    @Test void disabledAccountIsRejected() throws Exception {
        when(jdbc.queryForList("SELECT usuario,password_hash,rol,activo FROM usuarios WHERE usuario=?", "disabled"))
            .thenReturn(List.of(Map.of("usuario", "disabled", "password_hash", encoder.encode("correct-password"),
                                      "rol", "ADMIN", "activo", false)));
        mvc.perform(get("/api/admin/inventario").with(httpBasic("disabled", "correct-password")))
            .andExpect(status().isUnauthorized());
    }
    @Test void repeatedBadCredentialsAreLimitedBeforeTheDatabaseLookup() throws Exception {
        String username="rate-admin";
        when(jdbc.queryForList("SELECT usuario,password_hash,rol,activo FROM usuarios WHERE usuario=?", username))
            .thenReturn(List.of(Map.of("usuario",username,"password_hash",encoder.encode("correct-password"),"rol","ADMIN","activo",true)));
        for(int i=0;i<5;i++)mvc.perform(get("/api/admin/inventario").with(httpBasic(username,"wrong-password"))
                .with(request->{request.setRemoteAddr("192.0.2.17");return request;})).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/inventario").with(httpBasic(username,"correct-password"))
                .with(request->{request.setRemoteAddr("192.0.2.17");return request;}))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","2"));
        org.mockito.Mockito.verify(jdbc,org.mockito.Mockito.times(5))
            .queryForList("SELECT usuario,password_hash,rol,activo FROM usuarios WHERE usuario=?",username);
    }
    @Test void unavailableDatabaseIsNotReportedAsBadCredentialsOrCountedAsLoginFailures() throws Exception {
        String username="db-offline";
        when(jdbc.queryForList("SELECT usuario,password_hash,rol,activo FROM usuarios WHERE usuario=?",username))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private-database-details"));
        for(int i=0;i<7;i++)mvc.perform(get("/api/admin/inventario").with(httpBasic(username,"correct-password"))
                .with(request->{request.setRemoteAddr("192.0.2.18");return request;}))
            .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After","5"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-database-details"))));
    }
    @Test void operationalDiagnosticsAreOnlyAvailableToAdministrators() throws Exception {
        mvc.perform(get("/api/admin/diagnostico")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/diagnostico").with(user("seller").roles("VENDEDOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/diagnostico").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.entrega").value("1.6.1"))
            .andExpect(header().string("Cache-Control","no-store"));
    }
}
