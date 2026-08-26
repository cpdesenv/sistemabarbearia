package com.barbearia.assinatura.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CriarAssinaturaRequest(
        @NotNull(message = "O cliente e obrigatorio.")
        UUID clienteUuid,

        @NotNull(message = "O plano e obrigatorio.")
        UUID planoUuid) {
}
