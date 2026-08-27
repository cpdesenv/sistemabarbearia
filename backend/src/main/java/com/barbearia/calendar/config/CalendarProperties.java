package com.barbearia.calendar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "app.calendar")
@Getter
@Setter
public class CalendarProperties {

    private String gateway = "mock";
    private long outboxIntervaloMs = 30_000;
    private String frontendCallbackUri = "http://localhost:4200/configuracoes/integracoes/google-calendar";

    @NestedConfigurationProperty
    private final Google google = new Google();

    @Getter
    @Setter
    public static class Google {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String escopo = "https://www.googleapis.com/auth/calendar.events";
    }
}
