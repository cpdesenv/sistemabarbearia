package com.barbearia.agenda.dto;

import java.time.Instant;
import java.util.UUID;

public record SlotDisponivelDto(
        UUID profissionalUuid,
        String profissionalNome,
        String profissionalCorAgenda,
        Instant inicio,
        Instant fim) {
}
