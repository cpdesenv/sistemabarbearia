package com.barbearia.relatorio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code taxaDeRetorno} = clientesRecorrentes / (clientesNovos + clientesRecorrentes) x 100. */
public record RelatorioClientesDto(
        LocalDate dataInicial,
        LocalDate dataFinal,
        int clientesNovos,
        int clientesRecorrentes,
        int atendimentosTotais,
        BigDecimal taxaDeRetorno) {
}
