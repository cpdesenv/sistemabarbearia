package com.barbearia.assinatura.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Receita de assinatura (mensalidades do Clube Cavalinho recebidas no mes) vs.
 * receita avulsa (total de comandas fechadas no mesmo mes, incluindo itens de
 * assinante que extrapolaram o saldo) — ver {@code AssinaturaService#relatorioReceita}.
 */
public record RelatorioReceitaAssinaturaDto(
        YearMonth mesReferencia,
        BigDecimal receitaAssinaturas,
        BigDecimal receitaAvulsa) {
}
