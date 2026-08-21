package com.barbearia.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CriarDespesaRequest(
        @NotNull(message = "A data da despesa e obrigatoria.")
        LocalDate data,

        String categoria,

        @NotNull(message = "O valor da despesa e obrigatorio.")
        @DecimalMin(value = "0.00", message = "O valor nao pode ser negativo.")
        BigDecimal valor,

        String descricao,

        String comprovanteUrl) {
}
