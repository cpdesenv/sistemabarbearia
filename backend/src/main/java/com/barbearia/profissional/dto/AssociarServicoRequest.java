package com.barbearia.profissional.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AssociarServicoRequest(

        @NotNull(message = "O servico e obrigatorio.")
        UUID servicoUuid,

        @DecimalMin(value = "0", message = "A comissao nao pode ser negativa.")
        @DecimalMax(value = "100", message = "A comissao nao pode ser maior que 100%.")
        BigDecimal comissaoPercentual) {
}
