package com.barbearia.calendar.gateway;

/**
 * Fluxo OAuth2 de autorizacao com o Google (codigo de autorizacao -> refresh
 * token). Separado de {@link CalendarGateway} porque acontece uma unica vez
 * (ao conectar), enquanto o {@link CalendarGateway} e chamado a cada
 * criacao/atualizacao/remocao de evento.
 */
public interface GoogleOAuthGateway {

    String gerarUrlAutorizacao(String state);

    /** Troca o codigo de autorizacao pelo refresh token. Nunca retorna nem loga o access token. */
    String trocarCodigoPorRefreshToken(String codigo);
}
