package com.barbearia.relatorio.dto;

import java.math.BigDecimal;

public record LinhaFaturamentoDto(String nome, long quantidade, BigDecimal valorTotal, BigDecimal comissaoTotal) {
}
