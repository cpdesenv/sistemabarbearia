package com.barbearia.produto.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record EntradaEstoqueRequest(

        @NotNull(message = "A quantidade e obrigatoria.")
        @Min(value = 1, message = "A quantidade deve ser de pelo menos 1.")
        Integer quantidade,

        @DecimalMin(value = "0.00", message = "O custo unitario nao pode ser negativo.")
        BigDecimal custoUnitario,

        String motivo) {
}
