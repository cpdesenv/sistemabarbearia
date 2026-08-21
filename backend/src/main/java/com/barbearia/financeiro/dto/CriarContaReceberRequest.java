package com.barbearia.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CriarContaReceberRequest(
        @NotNull(message = "O cliente e obrigatorio.")
        UUID clienteUuid,

        String descricao,

        @NotNull(message = "O valor e obrigatorio.")
        @DecimalMin(value = "0.00", message = "O valor nao pode ser negativo.")
        BigDecimal valor,

        @NotNull(message = "A data de vencimento e obrigatoria.")
        LocalDate dataVencimento) {
}
