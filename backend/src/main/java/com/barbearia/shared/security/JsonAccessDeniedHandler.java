package com.barbearia.shared.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.barbearia.shared.exception.ErroRespostaEscritor;

/** Responde 403 em JSON quando o usuario autenticado nao tem o perfil exigido. */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ErroRespostaEscritor.escrever(response, 403, "ACESSO_NEGADO",
                "Voce nao tem permissao para acessar este recurso.", request.getRequestURI());
    }
}
