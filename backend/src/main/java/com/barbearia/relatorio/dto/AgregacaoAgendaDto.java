package com.barbearia.relatorio.dto;

/**
 * Linha transiente (profissional x dia) de contagem de agenda — persistida
 * em {@code RelatorioAgendaDiario} pelo job noturno, ou usada direto (sem
 * persistir) pela consulta ao vivo do dia corrente. Ver
 * {@code RelatorioAgregacaoService#calcularAgendaDoDia}.
 */
public record AgregacaoAgendaDto(
        Long profissionalId,
        String profissionalNome,
        int quantidadeFinalizados,
        int quantidadeCancelados,
        int quantidadeNaoCompareceu,
        int minutosCapacidade,
        int minutosOcupados) {
}
