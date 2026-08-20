package com.barbearia.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "O refresh token e obrigatorio.") String refreshToken) {
}
