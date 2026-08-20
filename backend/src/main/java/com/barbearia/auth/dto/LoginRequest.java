package com.barbearia.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "O e-mail e obrigatorio.") @Email(message = "Informe um e-mail valido.") String email,
        @NotBlank(message = "A senha e obrigatoria.") String senha) {
}
