package com.barbearia.profissional.dto;

import jakarta.validation.constraints.NotNull;

public record AtualizarStatusProfissionalRequest(@NotNull(message = "O campo ativo e obrigatorio.") Boolean ativo) {
}
