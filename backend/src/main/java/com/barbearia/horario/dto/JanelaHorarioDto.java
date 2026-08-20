package com.barbearia.horario.dto;

import java.time.LocalTime;

public record JanelaHorarioDto(int diaSemana, LocalTime horaInicio, LocalTime horaFim) {
}
