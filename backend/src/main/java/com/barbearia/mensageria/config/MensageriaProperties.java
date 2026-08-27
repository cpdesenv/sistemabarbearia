package com.barbearia.mensageria.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "whatsapp")
@Getter
@Setter
public class MensageriaProperties {

    private String gateway = "mock";
    private String webhookSecret = "dev-somente-troque-em-producao";
    private long outboxIntervaloMs = 5_000;

    /** Delay simulado (ms) para ENVIADA -> ENTREGUE -> LIDA, so no gateway mock. */
    private long simulacaoStatusDelayMs = 10_000;
}
