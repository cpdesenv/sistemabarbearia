package com.barbearia.financeiro.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AplicarDescontoRequest(
        @NotNull(message = "O valor do desconto e obrigatorio.")
        @DecimalMin(value = "0.00", message = "O desconto nao pode ser negativo.")
        BigDecimal valor,

        String motivo) {
}
