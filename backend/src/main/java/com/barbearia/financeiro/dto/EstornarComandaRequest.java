package com.barbearia.financeiro.dto;

import jakarta.validation.constraints.NotBlank;

public record EstornarComandaRequest(
        @NotBlank(message = "O motivo do estorno e obrigatorio.")
        String motivo) {
}
