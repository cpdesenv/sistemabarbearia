package com.barbearia.assinatura.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlanoAssinaturaDto(
        UUID uuid,
        String nome,
        String descricao,
        BigDecimal precoMensal,
        int cortesIncluidosPorCiclo,
        BigDecimal percentualDescontoAdicional,
        boolean ativo,
        List<UUID> servicosInclusosUuids,
        Instant criadoEm,
        Instant atualizadoEm) {
}
