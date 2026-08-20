package com.barbearia.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiraEmSegundos,
        UsuarioResumoDto usuario) {
}
