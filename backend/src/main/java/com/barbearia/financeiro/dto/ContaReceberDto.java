package com.barbearia.financeiro.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.barbearia.financeiro.domain.StatusContaReceber;

public record ContaReceberDto(
        UUID uuid,
        UUID clienteUuid,
        String clienteNome,
        String descricao,
        BigDecimal valor,
        LocalDate dataVencimento,
        StatusContaReceber status,
        LocalDate dataRecebimento,
        boolean vencida,
        Instant criadoEm,
        Instant atualizadoEm) {
}
