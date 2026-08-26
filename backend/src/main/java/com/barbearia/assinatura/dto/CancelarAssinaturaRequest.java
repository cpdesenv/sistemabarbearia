package com.barbearia.assinatura.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CancelarAssinaturaRequest(
        @NotBlank(message = "O motivo do cancelamento e obrigatorio.")
        String motivo,

        @NotNull(message = "A data de efeito do cancelamento e obrigatoria.")
        LocalDate dataEfeito) {
}
