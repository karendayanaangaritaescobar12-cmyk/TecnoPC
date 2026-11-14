package com.tecnopc.admin;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import static org.junit.jupiter.api.Assertions.*;

/** Real local HTTP and Tomcat; no database, Render access, or payment creation. */
class TrustedProxyHttpTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class, SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class})
    static class TestApp {
        @Bean FilterRegistrationBean<PublicRequestFilter> publicLimits() {
            return new FilterRegistrationBean<>(new PublicRequestFilter());
        }
        @Bean ServletRegistrationBean<HttpServlet> probe() {
            return new ServletRegistrationBean<>(new HttpServlet() {
                @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
                    res.getWriter().write(req.getRemoteAddr() + "|" + req.isSecure());
                }
            }, "/api/public/pagos");
        }
    }

    private ServletWebServerApplicationContext start(String internalProxies) {
        return (ServletWebServerApplicationContext) SpringApplication.run(TestApp.class,
            "--spring.profiles.active=hosted,hosted-proxy", "--server.port=0",
            "--server.address=127.0.0.1", "--spring.main.banner-mode=off", "--debug=false",
            "--logging.level.org.springframework=INFO",
            "--RENDER_INTERNAL_PROXIES_REGEX=" + internalProxies,
            "--RENDER_TRUSTED_PROXIES_REGEX=192[.]0[.]2[.]254");
    }

    private HttpResponse<String> send(HttpClient client, int port, String forwarded) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/public/pagos"))
            .timeout(Duration.ofSeconds(10))
            .header("X-Forwarded-For", forwarded).header("X-Forwarded-Proto", "https")
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void trustedProxySeparatesClientsAndSpoofedPrefixesCannotResetQuota() throws Exception {
        try (var context = start("127[.]0[.]0[.]1")) {
            var client = HttpClient.newHttpClient();
            int port = context.getWebServer().getPort();
            for (int i = 0; i < 10; i++) {
                var response = send(client, port, "203.0.113." + (i + 1) + ", 198.51.100.10, 192.0.2.254");
                assertEquals(200, response.statusCode());
                assertEquals("198.51.100.10|true", response.body());
            }
            assertEquals(429, send(client, port, "203.0.113.99, 198.51.100.10, 192.0.2.254").statusCode());
            var other = send(client, port, "198.51.100.11, 192.0.2.254");
            assertEquals(200, other.statusCode());
            assertEquals("198.51.100.11|true", other.body());
        }
    }

    @Test void untrustedConnectionCannotSpoofAddressOrHttpsOrResetQuota() throws Exception {
        try (var context = start("192[.]0[.]2[.]253")) {
            var client = HttpClient.newHttpClient();
            int port = context.getWebServer().getPort();
            for (int i = 0; i < 10; i++) {
                var response = send(client, port, "198.51.100." + (i + 1));
                assertEquals(200, response.statusCode());
                assertEquals("127.0.0.1|false", response.body());
            }
            assertEquals(429, send(client, port, "198.51.100.99").statusCode());
        }
    }
}
