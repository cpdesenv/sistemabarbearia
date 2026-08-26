package com.barbearia.financeiro.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.barbearia.financeiro.domain.TipoItemComanda;

public record ComandaItemDto(
        UUID uuid,
        TipoItemComanda tipo,
        UUID servicoUuid,
        UUID produtoUuid,
        String descricao,
        int quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorBruto,
        BigDecimal valorDescontoRateado,
        BigDecimal valorLiquido,
        BigDecimal comissaoPercentualAplicado,
        BigDecimal comissaoValor,
        boolean cobertoPorAssinatura) {
}
