package com.barbearia.relatorio.dto;

import java.math.BigDecimal;

import com.barbearia.financeiro.domain.FormaPagamento;

/**
 * Projecao de uma linha agregada (profissional x servico x forma de
 * pagamento) para um periodo — usada tanto pelo job noturno
 * ({@code RelatorioAgregacaoService#agregarDia}, persistida em
 * {@code RelatorioServicoDiario}) quanto pela consulta ao vivo do dia
 * corrente (ainda nao agregado, ver {@code RelatorioFaturamentoService}).
 */
public record AgregacaoServicoDto(
        Long profissionalId,
        String profissionalNome,
        Long servicoId,
        String servicoNome,
        FormaPagamento formaPagamento,
        long quantidade,
        BigDecimal valorTotal,
        BigDecimal comissaoTotal) {
}
