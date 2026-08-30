package com.barbearia.relatorio.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record ReprocessarRelatorioRequest(
        @NotNull(message = "A data inicial e obrigatoria.")
        LocalDate dataInicial,

        @NotNull(message = "A data final e obrigatoria.")
        LocalDate dataFinal) {
}
