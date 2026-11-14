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

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:startup-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "ADMIN_USER=admin-test","ADMIN_PASSWORD=long-test-password-only", "EMAIL_ENABLED=false","WHATSAPP_ENABLED=false"})
@AutoConfigureMockMvc
class StartupIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.context.ApplicationContext context;

    @Test void freshStartCreatesSchemaAndFirstAdminBeforeServingStore() throws Exception {
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE usuario='admin-test' AND rol='ADMIN'",Integer.class));
        mvc.perform(get("/healthz")).andExpect(status().isOk());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/notificaciones").with(httpBasic("admin-test","long-test-password-only"))).andExpect(status().isOk());
    }
    @Test void realHttpRegistrationCommitsContactAndOutboxInSameTransaction() throws Exception {
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM notificaciones",Integer.class);
        mvc.perform(post("/api/public/solicitudes").contentType("application/json")
            .content("{\"tipo\":\"servicio\",\"nombre\":\"Ana\",\"telefono\":\"3001234567\",\"detalle\":\"No prende el equipo\",\"contacto\":{\"correo\":\"cliente@example.com\",\"whatsapp\":\"+573001234567\",\"avisosCorreo\":true,\"avisosWhatsapp\":true}}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber());
        assertEquals(before+2,jdbc.queryForObject("SELECT COUNT(*) FROM notificaciones",Integer.class));
    }
    @Test void techniciansCannotSeeNotificationsOrMarkSalesReady() throws Exception {
        mvc.perform(get("/api/admin/notificaciones")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/vendedor/pedidos/1/listo").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void applicationDoesNotEnableXsltViewsOrServerSentViewFragments() {
        assertTrue(context.getBeansOfType(org.springframework.web.servlet.view.xslt.XsltViewResolver.class).isEmpty());
        var mappings=context.getBean("requestMappingHandlerMapping",org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
        mappings.getHandlerMethods().forEach((mapping,handler)->{
            if(!handler.getBeanType().getPackageName().startsWith("com.tecnopc"))return;
            assertTrue(org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(handler.getBeanType(),org.springframework.web.bind.annotation.RestController.class));
            assertFalse(org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class.isAssignableFrom(handler.getMethod().getReturnType()));
            assertFalse(mapping.getProducesCondition().getProducibleMediaTypes().contains(org.springframework.http.MediaType.TEXT_EVENT_STREAM));
        });
    }
}
