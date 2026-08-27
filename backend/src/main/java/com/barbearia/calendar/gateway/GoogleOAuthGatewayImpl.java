package com.barbearia.calendar.gateway;

import java.io.IOException;
import java.util.Collections;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.barbearia.calendar.config.CalendarProperties;

/**
 * Fluxo OAuth2 real com o Google (Authorization Code flow, sem DataStore —
 * o unico segredo que precisa sobreviver e o refresh token, ja persistido
 * criptografado por {@code IntegracaoGoogleCalendarService}).
 */
@Component
@ConditionalOnProperty(prefix = "app.calendar", name = "gateway", havingValue = "google")
public class GoogleOAuthGatewayImpl implements GoogleOAuthGateway {

    private final CalendarProperties propriedades;

    public GoogleOAuthGatewayImpl(CalendarProperties propriedades) {
        this.propriedades = propriedades;
    }

    @Override
    public String gerarUrlAutorizacao(String state) {
        CalendarProperties.Google google = propriedades.getGoogle();
        return new GoogleAuthorizationCodeRequestUrl(google.getClientId(), google.getRedirectUri(),
                Collections.singleton(google.getEscopo()))
                .setState(state)
                .setAccessType("offline")
                .set("prompt", "consent")
                .build();
    }

    @Override
    public String trocarCodigoPorRefreshToken(String codigo) {
        CalendarProperties.Google google = propriedades.getGoogle();
        try {
            GoogleTokenResponse resposta = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance(),
                    google.getClientId(), google.getClientSecret(), codigo, google.getRedirectUri())
                    .execute();
            return resposta.getRefreshToken();
        } catch (IOException e) {
            throw new CalendarSyncException("Falha ao trocar codigo de autorizacao por refresh token.", e);
        }
    }
}
