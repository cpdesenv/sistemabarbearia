package com.barbearia.relatorio.dto;

import java.math.BigDecimal;

public record LinhaAgendaDto(
        String profissionalNome,
        int quantidadeFinalizados,
        int quantidadeCancelados,
        int quantidadeNaoCompareceu,
        BigDecimal taxaOcupacao) {
}
