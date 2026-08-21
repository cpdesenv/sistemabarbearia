package com.barbearia.financeiro.dto;

import java.math.BigDecimal;

public record FluxoCaixaDto(
        BigDecimal caixaEmMaos,
        BigDecimal contasAReceberEsperadas,
        BigDecimal contasAPagarVencidas,
        BigDecimal fluxoCaixa) {
}
