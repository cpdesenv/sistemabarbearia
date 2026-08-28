package com.barbearia.mensageria.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.barbearia.mensageria.domain.ModoAtendimento;

public record ConversaDto(
        UUID uuid,
        UUID clienteUuid,
        String clienteNome,
        String telefoneE164,
        Instant ultimaMensagemEm,
        ModoAtendimento modoAtendimento,
        String motivoEscalonamento,
        int turnosIa,
        BigDecimal custoLlmAcumuladoCentavos) {
}
