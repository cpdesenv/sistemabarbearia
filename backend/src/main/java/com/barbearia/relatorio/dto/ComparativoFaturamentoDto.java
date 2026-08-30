package com.barbearia.relatorio.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * {@code variacaoPercentual*} e' {@code null} quando o periodo base de
 * comparacao teve valor zero (mesma convencao do dashboard, Fase 10).
 */
public record ComparativoFaturamentoDto(
        YearMonth mes,
        BigDecimal valorMesAtual,
        BigDecimal valorMesAnterior,
        BigDecimal variacaoPercentualMesAnterior,
        BigDecimal valorMesmoMesAnoAnterior,
        BigDecimal variacaoPercentualAnoAnterior) {
}
