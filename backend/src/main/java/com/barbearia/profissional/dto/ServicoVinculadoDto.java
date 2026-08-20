package com.barbearia.profissional.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ServicoVinculadoDto(
        UUID servicoUuid,
        String nomeServico,
        BigDecimal comissaoPercentual,
        BigDecimal comissaoEfetiva) {
}
