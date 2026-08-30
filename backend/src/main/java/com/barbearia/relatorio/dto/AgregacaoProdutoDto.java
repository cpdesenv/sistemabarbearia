package com.barbearia.relatorio.dto;

import java.math.BigDecimal;

/**
 * Projecao de uma linha agregada (produto) para um periodo — usada tanto
 * pelo job noturno ({@code RelatorioAgregacaoService#agregarDia}, persistida
 * em {@code RelatorioProdutoDiario}) quanto pela consulta ao vivo do dia
 * corrente (ainda nao agregado, ver {@code RelatorioProdutoService}).
 */
public record AgregacaoProdutoDto(
        Long produtoId,
        String produtoNome,
        long quantidadeVendida,
        BigDecimal valorTotal,
        BigDecimal custoTotal) {
}
