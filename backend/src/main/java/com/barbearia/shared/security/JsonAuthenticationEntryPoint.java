package com.barbearia.shared.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.barbearia.shared.exception.ErroRespostaEscritor;

/** Responde 401 em JSON quando uma rota protegida e' acessada sem token valido. */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        ErroRespostaEscritor.escrever(response, 401, "NAO_AUTENTICADO",
                "Autenticacao necessaria para acessar este recurso.", request.getRequestURI());
    }
}
