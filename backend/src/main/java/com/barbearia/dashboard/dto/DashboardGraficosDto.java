package com.barbearia.dashboard.dto;

import java.util.List;

import com.barbearia.financeiro.dto.TotalPorFormaPagamentoDto;

public record DashboardGraficosDto(
        List<PontoMensalDto> faturamentoUltimos12Meses,
        List<ItemContagemDto> servicosMaisVendidos,
        List<ItemContagemDto> atendimentosPorProfissional,
        List<TotalPorFormaPagamentoDto> distribuicaoFormaPagamento) {
}
