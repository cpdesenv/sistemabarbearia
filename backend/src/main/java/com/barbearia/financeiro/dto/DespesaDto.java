package com.barbearia.financeiro.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DespesaDto(
        UUID uuid,
        LocalDate data,
        String categoria,
        BigDecimal valor,
        String descricao,
        String comprovanteUrl,
        Instant criadoEm) {
}
