package com.barbearia.shared.exception;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Escreve {@link ErroResposta} diretamente na resposta HTTP para os casos que
 * acontecem fora do fluxo normal do Spring MVC (filtros de seguranca e de
 * rate limiting rodam antes do DispatcherServlet, entao excecoes lancadas ali
 * nao chegam ao {@code @RestControllerAdvice}).
 */
public final class ErroRespostaEscritor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private ErroRespostaEscritor() {
    }

    public static void escrever(HttpServletResponse response, int status, String erro, String mensagem,
            String caminho) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        ErroResposta corpo = ErroResposta.de(status, erro, mensagem, caminho);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(corpo));
    }
}
