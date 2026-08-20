package com.barbearia.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolve o IP real do cliente considerando que, em producao, o backend fica
 * atras do Nginx do container do frontend (proxy reverso), entao o IP de
 * conexao TCP e' o do proxy, nao o do cliente final.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolver(HttpServletRequest request) {
        String encaminhadoPor = request.getHeader("X-Forwarded-For");
        if (encaminhadoPor != null && !encaminhadoPor.isBlank()) {
            return encaminhadoPor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
