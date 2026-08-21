package com.barbearia.produto.dto;

import jakarta.validation.constraints.NotNull;

public record AtualizarStatusProdutoRequest(@NotNull(message = "O campo ativo e obrigatorio.") Boolean ativo) {
}
