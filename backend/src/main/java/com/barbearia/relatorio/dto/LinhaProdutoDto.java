package com.barbearia.relatorio.dto;

import java.math.BigDecimal;

public record LinhaProdutoDto(
        String nome,
        long quantidadeVendida,
        BigDecimal valorTotal,
        BigDecimal custoTotal,
        BigDecimal margemTotal,
        BigDecimal margemPercentual) {
}
