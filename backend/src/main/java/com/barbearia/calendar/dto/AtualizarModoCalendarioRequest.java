package com.barbearia.calendar.dto;

import jakarta.validation.constraints.NotNull;

import com.barbearia.calendar.domain.ModoCalendario;

public record AtualizarModoCalendarioRequest(

        @NotNull(message = "O modo e obrigatorio.")
        ModoCalendario modo,

        String calendarioIdUnico) {
}
