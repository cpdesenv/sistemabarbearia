package com.barbearia.relatorio.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.financeiro.domain.StatusComanda;
import com.barbearia.financeiro.repository.ComandaRepository;
import com.barbearia.relatorio.domain.RelatorioServicoDiario;
import com.barbearia.relatorio.repository.RelatorioServicoDiarioRepository;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Poe as tabelas de agregacao diaria (Fase 11) em dia. {@link #agregarDia}
 * e' idempotente — apaga e recalcula as linhas do dia informado — para poder
 * ser chamado tanto pelo job noturno ({@code RelatorioAgregacaoScheduler},
 * so' o dia anterior) quanto pelo endpoint de reprocessamento (backfill do
 * historico apos o deploy desta fase, ou correcao apos um estorno tardio
 * num dia ja agregado).
 */
@Service
@RequiredArgsConstructor
public class RelatorioAgregacaoService {

    private final BarbeariaRepository barbeariaRepository;
    private final ComandaRepository comandaRepository;
    private final RelatorioServicoDiarioRepository relatorioServicoDiarioRepository;

    @Transactional
    public void agregarDia(LocalDate data) {
        ZoneId fuso = obterFusoHorario();
        Instant inicio = data.atStartOfDay(fuso).toInstant();
        Instant fim = data.plusDays(1).atStartOfDay(fuso).toInstant();

        relatorioServicoDiarioRepository.deleteByData(data);

        comandaRepository.agregarServicosPorPeriodo(StatusComanda.FECHADA, inicio, fim).stream()
                .map(agregacao -> new RelatorioServicoDiario(data, agregacao))
                .forEach(relatorioServicoDiarioRepository::save);
    }

    @Transactional
    public void reprocessar(LocalDate dataInicial, LocalDate dataFinal) {
        if (dataFinal.isBefore(dataInicial)) {
            throw new NegocioException("A data final nao pode ser anterior a data inicial.");
        }
        for (LocalDate data = dataInicial; !data.isAfter(dataFinal); data = data.plusDays(1)) {
            agregarDia(data);
        }
    }

    private ZoneId obterFusoHorario() {
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
        return ZoneId.of(barbearia.getFusoHorario());
    }
}
