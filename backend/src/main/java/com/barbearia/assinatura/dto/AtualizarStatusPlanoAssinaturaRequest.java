package com.barbearia.assinatura.dto;

import jakarta.validation.constraints.NotNull;

public record AtualizarStatusPlanoAssinaturaRequest(
        @NotNull(message = "O status ativo/inativo e obrigatorio.")
        Boolean ativo) {
}
