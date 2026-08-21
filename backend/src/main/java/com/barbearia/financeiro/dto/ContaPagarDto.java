package com.barbearia.financeiro.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.barbearia.financeiro.domain.StatusContaPagar;

public record ContaPagarDto(
        UUID uuid,
        String descricao,
        BigDecimal valor,
        LocalDate dataVencimento,
        StatusContaPagar status,
        LocalDate dataPagamento,
        boolean vencida,
        Instant criadoEm,
        Instant atualizadoEm) {
}
