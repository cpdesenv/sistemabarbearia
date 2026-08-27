package com.barbearia.calendar.gateway;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simula o fluxo OAuth2 sem nenhuma credencial Google — permite testar
 * conectar/desconectar/ressincronizar em dev e na suite de testes.
 */
@Component
@ConditionalOnProperty(prefix = "app.calendar", name = "gateway", havingValue = "mock", matchIfMissing = true)
public class MockGoogleOAuthGateway implements GoogleOAuthGateway {

    private static final Logger log = LoggerFactory.getLogger(MockGoogleOAuthGateway.class);

    @Override
    public String gerarUrlAutorizacao(String state) {
        return "https://mock-google-oauth.invalid/consentimento?state=" + state;
    }

    @Override
    public String trocarCodigoPorRefreshToken(String codigo) {
        String refreshToken = "mock-refresh-token-" + UUID.randomUUID();
        log.info("[MOCK OAUTH] Codigo trocado por refresh token simulado");
        return refreshToken;
    }
}
