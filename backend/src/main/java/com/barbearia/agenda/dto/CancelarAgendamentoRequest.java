package com.barbearia.agenda.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelarAgendamentoRequest(
        @NotBlank(message = "O motivo do cancelamento e obrigatorio.") String motivo) {
}
