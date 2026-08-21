package com.barbearia.produto.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.barbearia.produto.domain.TipoMovimentoEstoque;

public record MovimentoEstoqueDto(
        TipoMovimentoEstoque tipo,
        int quantidade,
        BigDecimal custoUnitario,
        String motivo,
        Long comandaId,
        Instant criadoEm) {
}
