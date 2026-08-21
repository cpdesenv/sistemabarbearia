package com.barbearia.agenda.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.barbearia.agenda.domain.OrigemAgendamento;
import com.barbearia.agenda.domain.StatusAgendamento;

public record AgendamentoDto(
        UUID uuid,
        UUID clienteUuid,
        String clienteNome,
        String clienteTelefone,
        UUID profissionalUuid,
        String profissionalNome,
        String profissionalCorAgenda,
        List<AgendamentoServicoDto> servicos,
        Instant inicio,
        Instant fim,
        BigDecimal valorTotal,
        StatusAgendamento status,
        OrigemAgendamento origem,
        String observacao,
        String motivoCancelamento,
        Instant criadoEm,
        Instant atualizadoEm) {
}
