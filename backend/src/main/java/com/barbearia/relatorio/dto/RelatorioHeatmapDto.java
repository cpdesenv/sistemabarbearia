package com.barbearia.relatorio.dto;

import java.time.LocalDate;
import java.util.List;

public record RelatorioHeatmapDto(LocalDate dataInicial, LocalDate dataFinal, List<CelulaHeatmapDto> celulas) {
}
