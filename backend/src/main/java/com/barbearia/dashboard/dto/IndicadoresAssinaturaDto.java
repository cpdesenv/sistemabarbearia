package com.barbearia.dashboard.dto;

import java.math.BigDecimal;

/**
 * {@code taxaChurnMes} = assinaturas canceladas no mes / (ativas + inadimplentes
 * atuais + canceladas no mes) x 100 — uma aproximacao, ja que o sistema nao
 * mantem um snapshot historico de quantas assinaturas estavam em curso no
 * inicio do mes (ver {@code DashboardService#montarIndicadoresAssinatura}).
 */
public record IndicadoresAssinaturaDto(
        BigDecimal receitaRecorrente,
        BigDecimal taxaChurnMes) {
}
