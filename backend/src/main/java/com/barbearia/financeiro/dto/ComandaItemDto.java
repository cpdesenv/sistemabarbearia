package com.barbearia.financeiro.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ComandaItemDto(
        UUID uuid,
        UUID servicoUuid,
        String descricao,
        int quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorBruto,
        BigDecimal valorDescontoRateado,
        BigDecimal valorLiquido,
        BigDecimal comissaoPercentualAplicado,
        BigDecimal comissaoValor) {
}
