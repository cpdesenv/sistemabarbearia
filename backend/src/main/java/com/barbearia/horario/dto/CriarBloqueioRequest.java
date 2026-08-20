package com.barbearia.horario.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarBloqueioRequest(

        UUID profissionalUuid,

        @NotNull(message = "O inicio do bloqueio e obrigatorio.")
        Instant inicio,

        @NotNull(message = "O fim do bloqueio e obrigatorio.")
        Instant fim,

        @NotBlank(message = "O motivo e obrigatorio.")
        @Size(max = 200, message = "O motivo deve ter no maximo 200 caracteres.")
        String motivo) {
}
