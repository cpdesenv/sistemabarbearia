package com.barbearia.dashboard.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Um ponto do grafico de faturamento dos ultimos 12 meses. */
public record PontoMensalDto(YearMonth mes, BigDecimal valor) {
}
