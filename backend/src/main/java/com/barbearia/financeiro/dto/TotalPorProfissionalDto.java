package com.barbearia.financeiro.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TotalPorProfissionalDto(
        UUID profissionalUuid,
        String profissionalNome,
        BigDecimal totalFaturado,
        BigDecimal totalComissao) {
}
