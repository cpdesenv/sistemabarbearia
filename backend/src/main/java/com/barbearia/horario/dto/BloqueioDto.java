package com.barbearia.horario.dto;

import java.time.Instant;
import java.util.UUID;

public record BloqueioDto(
        UUID uuid,
        UUID profissionalUuid,
        String profissionalNome,
        Instant inicio,
        Instant fim,
        String motivo,
        Instant criadoEm) {
}
