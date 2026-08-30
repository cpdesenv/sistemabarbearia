package com.barbearia.dashboard.dto;

import java.math.BigDecimal;

/**
 * {@code percentualFaturamentoVsMesAnterior} e' {@code null} quando o mes
 * anterior nao teve faturamento (divisao por zero nao faz sentido nesse
 * caso — o frontend trata a ausencia do valor, nao um "infinito").
 */
public record DashboardCardsDto(
        BigDecimal faturamentoDia,
        BigDecimal faturamentoMes,
        BigDecimal percentualFaturamentoVsMesAnterior,
        long atendimentosDia,
        BigDecimal ticketMedioDia,
        BigDecimal taxaOcupacaoHoje) {
}
