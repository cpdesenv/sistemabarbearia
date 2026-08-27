package com.barbearia.calendar.dto;

import jakarta.validation.constraints.Size;

public record DefinirCalendarioProfissionalRequest(

        @Size(max = 255, message = "O id do calendario deve ter no maximo 255 caracteres.")
        String googleCalendarId) {
}
