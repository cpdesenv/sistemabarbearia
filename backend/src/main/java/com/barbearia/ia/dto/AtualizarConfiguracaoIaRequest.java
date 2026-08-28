package com.barbearia.ia.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AtualizarConfiguracaoIaRequest(

        @NotNull(message = "O campo 'ativo' e obrigatorio.")
        Boolean ativo,

        @Min(value = 1, message = "O limite de turnos deve ser de pelo menos 1.")
        int limiteTurnos,

        @Min(value = 0, message = "O teto de custo mensal nao pode ser negativo.")
        long tetoCustoMensalCentavos) {
}
