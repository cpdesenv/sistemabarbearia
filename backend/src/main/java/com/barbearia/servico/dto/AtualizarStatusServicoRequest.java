package com.barbearia.servico.dto;

import jakarta.validation.constraints.NotNull;

public record AtualizarStatusServicoRequest(@NotNull(message = "O campo ativo e obrigatorio.") Boolean ativo) {
}
