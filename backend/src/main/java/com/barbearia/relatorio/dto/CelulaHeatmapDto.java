package com.barbearia.relatorio.dto;

/** {@code diaSemana} segue {@link java.time.DayOfWeek#getValue()} (1 = segunda, 7 = domingo). */
public record CelulaHeatmapDto(int diaSemana, int hora, long quantidadeFinalizados) {
}
