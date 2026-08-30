package com.barbearia.relatorio.dto;

/**
 * Quantidade de agendamentos FINALIZADO numa hora (0-23) de um dia especifico
 * — usada tanto pelo job noturno ({@code RelatorioAgregacaoService#agregarDia},
 * persistida em {@code RelatorioHorarioDiario}) quanto pela consulta ao vivo
 * do dia corrente, ver {@code RelatorioHeatmapService}.
 */
public record AgregacaoHorarioDto(int hora, long quantidadeFinalizados) {
}
