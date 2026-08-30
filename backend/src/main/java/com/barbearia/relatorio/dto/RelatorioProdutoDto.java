package com.barbearia.relatorio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RelatorioProdutoDto(
        LocalDate dataInicial,
        LocalDate dataFinal,
        BigDecimal valorTotal,
        BigDecimal custoTotal,
        BigDecimal margemTotal,
        BigDecimal margemPercentual,
        List<LinhaProdutoDto> porProduto) {
}
