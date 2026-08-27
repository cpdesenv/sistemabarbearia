package com.barbearia.calendar.dto;

import java.time.Instant;

import com.barbearia.calendar.domain.ModoCalendario;

public record StatusIntegracaoDto(
        boolean conectado,
        ModoCalendario modo,
        String calendarioIdUnico,
        Instant conectadoEm,
        String ultimoErro) {
}
