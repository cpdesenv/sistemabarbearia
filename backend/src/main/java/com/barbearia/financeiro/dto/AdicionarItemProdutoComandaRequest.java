package com.barbearia.financeiro.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdicionarItemProdutoComandaRequest(
        @NotNull(message = "O produto e obrigatorio.")
        UUID produtoUuid,

        @Min(value = 1, message = "A quantidade deve ser ao menos 1.")
        Integer quantidade) {

    public int quantidadeOuPadrao() {
        return quantidade == null ? 1 : quantidade;
    }
}
