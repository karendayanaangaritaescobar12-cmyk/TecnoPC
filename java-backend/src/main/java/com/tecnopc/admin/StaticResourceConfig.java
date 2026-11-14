package com.tecnopc.admin;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class StaticResourceConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        var cache = CacheControl.maxAge(Duration.ZERO).cachePublic().mustRevalidate();
        for (String directory : new String[]{"css", "js", "images"}) {
            registry.addResourceHandler("/" + directory + "/**")
                .addResourceLocations("classpath:/static/" + directory + "/")
                .setCacheControl(cache);
        }
    }
}
