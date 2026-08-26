package com.barbearia.assinatura.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.barbearia.assinatura.domain.StatusAssinatura;

public record AssinaturaDto(
        UUID uuid,
        UUID clienteUuid,
        String clienteNome,
        UUID planoUuid,
        String planoNome,
        StatusAssinatura status,
        int saldoCortesAtual,
        LocalDate dataInicio,
        LocalDate dataProximaRenovacao,
        LocalDate dataCancelamento,
        String motivoCancelamento,
        Instant criadoEm,
        Instant atualizadoEm) {
}
