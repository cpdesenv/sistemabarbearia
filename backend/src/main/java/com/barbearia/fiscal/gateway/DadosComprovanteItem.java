package com.barbearia.fiscal.gateway;

import java.math.BigDecimal;

public record DadosComprovanteItem(
        String descricao,
        int quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorLiquido) {
}
