package com.barbearia.financeiro.dto;

import jakarta.validation.constraints.NotNull;

import com.barbearia.financeiro.domain.FormaPagamento;

public record DefinirFormaPagamentoRequest(
        @NotNull(message = "A forma de pagamento e obrigatoria.")
        FormaPagamento formaPagamento) {
}
