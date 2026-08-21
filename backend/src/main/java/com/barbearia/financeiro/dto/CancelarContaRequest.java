package com.barbearia.financeiro.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelarContaRequest(
        @NotBlank(message = "O motivo do cancelamento e obrigatorio.")
        String motivo) {
}
