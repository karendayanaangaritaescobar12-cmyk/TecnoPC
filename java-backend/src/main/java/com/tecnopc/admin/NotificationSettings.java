package com.tecnopc.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tecnopc.notifications")
public record NotificationSettings(Email email, Whatsapp whatsapp) {
    public record Email(boolean enabled,String apiKey,String from) {}
    public record Whatsapp(boolean enabled,String token,String phoneNumberId,String apiVersion,String template,String language) {}
}
