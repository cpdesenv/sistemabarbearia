package com.barbearia.relatorio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RelatorioAgendaDto(
        LocalDate dataInicial,
        LocalDate dataFinal,
        int quantidadeFinalizados,
        int quantidadeCancelados,
        int quantidadeNaoCompareceu,
        BigDecimal taxaOcupacao,
        List<LinhaAgendaDto> porProfissional) {
}
