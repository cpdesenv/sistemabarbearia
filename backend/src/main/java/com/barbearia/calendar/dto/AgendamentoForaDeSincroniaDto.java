package com.barbearia.calendar.dto;

import java.time.Instant;
import java.util.UUID;

import com.barbearia.calendar.domain.StatusOutbox;
import com.barbearia.calendar.domain.TipoOperacaoOutbox;

public record AgendamentoForaDeSincroniaDto(
        UUID agendamentoUuid,
        String clienteNome,
        Instant inicioAgendamento,
        TipoOperacaoOutbox tipoOperacao,
        StatusOutbox status,
        int tentativas,
        String ultimoErro) {
}
