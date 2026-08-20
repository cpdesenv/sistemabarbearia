package com.barbearia.horario.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SalvarJanelaHorarioRequest(

        @NotNull(message = "O dia da semana e obrigatorio.")
        @Min(value = 1, message = "O dia da semana deve ser entre 1 (segunda) e 7 (domingo).")
        @Max(value = 7, message = "O dia da semana deve ser entre 1 (segunda) e 7 (domingo).")
        Integer diaSemana,

        @NotNull(message = "O horario inicial e obrigatorio.")
        LocalTime horaInicio,

        @NotNull(message = "O horario final e obrigatorio.")
        LocalTime horaFim) {
}
