package com.tecnopc.admin;

import com.mysql.cj.conf.ConnectionUrl;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.junit.jupiter.api.Assertions.*;

class CloudConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DataSourceProperties.class)
    static class Binding {}

    @Test void cloudProfileReplacesLocalUrlAndRequiresVerifiedTls() {
        new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Binding.class)
            .withPropertyValues("spring.profiles.active=cloud", "DB_HOST=db.example.invalid",
                "DB_USER=prefix.tecnopc_app", "DB_PASSWORD=test-not-real",
                "DB_URL=jdbc:mysql://localhost:3306/other?sslMode=DISABLED")
            .run(context -> {
                assertNull(context.getStartupFailure());
                var datasource = context.getBean(DataSourceProperties.class);
                var connection = ConnectionUrl.getConnectionUrlInstance(datasource.getUrl(), new Properties()).getMainHost();
                assertEquals("db.example.invalid", connection.getHost());
                assertEquals(4000, connection.getPort());
                assertEquals("tecnopc", connection.getDatabase());
                assertEquals("VERIFY_IDENTITY", connection.getHostProperties().get("sslMode"));
                assertEquals("UTC", connection.getHostProperties().get("connectionTimeZone"));
                assertEquals("true", connection.getHostProperties().get("forceConnectionTimeZoneToSession"));
                assertEquals("prefix.tecnopc_app", datasource.getUsername());
                assertFalse(datasource.getUrl().contains(datasource.getPassword()));
                assertEquals("5000",connection.getHostProperties().get("connectTimeout"));
                assertEquals("10000",connection.getHostProperties().get("socketTimeout"));
                assertEquals("3",context.getEnvironment().getProperty("spring.datasource.hikari.maximum-pool-size"));
                assertEquals("3000",context.getEnvironment().getProperty("spring.datasource.hikari.connection-timeout"));
                assertEquals("5s",context.getEnvironment().getProperty("spring.jdbc.template.query-timeout"));
                assertEquals("3",context.getEnvironment().getProperty("spring.flyway.connect-retries"));
            });
    }
}
