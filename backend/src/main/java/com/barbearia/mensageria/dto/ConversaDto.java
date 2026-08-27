package com.barbearia.mensageria.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversaDto(
        UUID uuid,
        UUID clienteUuid,
        String clienteNome,
        String telefoneE164,
        Instant ultimaMensagemEm) {
}
