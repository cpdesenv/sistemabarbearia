package com.barbearia.relatorio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.barbearia.financeiro.dto.TotalPorFormaPagamentoDto;

public record RelatorioFaturamentoDto(
        LocalDate dataInicial,
        LocalDate dataFinal,
        BigDecimal valorTotal,
        BigDecimal comissaoTotal,
        long quantidadeAtendimentos,
        List<LinhaFaturamentoDto> porServico,
        List<LinhaFaturamentoDto> porProfissional,
        List<TotalPorFormaPagamentoDto> porFormaPagamento) {
}
