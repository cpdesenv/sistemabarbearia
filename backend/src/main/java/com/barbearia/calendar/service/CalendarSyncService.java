package com.barbearia.calendar.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.calendar.domain.AgendamentoCalendarOutbox;
import com.barbearia.calendar.domain.StatusOutbox;
import com.barbearia.calendar.domain.TipoOperacaoOutbox;
import com.barbearia.calendar.repository.AgendamentoCalendarOutboxRepository;

/**
 * Enfileira a intencao de sincronizar um agendamento com o Google Calendar,
 * chamado de dentro da mesma transacao de {@code AgendamentoService}
 * (confirmar/alterar/cancelar) — padrao outbox transacional: a linha so
 * existe se a mudanca no agendamento realmente foi commitada.
 *
 * <p>O worker ({@code CalendarOutboxWorker}) sempre le o estado atual do
 * agendamento no momento de processar, entao aqui basta registrar QUE existe
 * trabalho pendente e de qual tipo — nunca um payload congelado no momento do
 * enfileiramento.
 */
@Service
@RequiredArgsConstructor
public class CalendarSyncService {

    private final AgendamentoCalendarOutboxRepository outboxRepository;

    @Transactional
    public void enfileirar(Agendamento agendamento, TipoOperacaoOutbox tipoOperacao) {
        Optional<AgendamentoCalendarOutbox> pendente = outboxRepository.findByAgendamentoAndStatus(agendamento,
                StatusOutbox.PENDENTE);

        AgendamentoCalendarOutbox linha = pendente.orElseGet(AgendamentoCalendarOutbox::new);
        linha.setAgendamento(agendamento);
        linha.setTipoOperacao(tipoOperacao);
        linha.setTentativas(0);
        linha.setProximaTentativaEm(Instant.now());
        linha.setUltimoErro(null);
        outboxRepository.save(linha);
    }

    /** Cancela uma pendencia de CRIAR que nunca chegou a virar evento de verdade (sem googleEventId ainda). */
    @Transactional
    public void cancelarPendenciaSemEvento(Agendamento agendamento) {
        outboxRepository.findByAgendamentoAndStatus(agendamento, StatusOutbox.PENDENTE)
                .ifPresent(linha -> {
                    linha.setStatus(StatusOutbox.CONCLUIDO);
                    linha.setUltimoErro("Cancelado antes de sincronizar com o Google Calendar.");
                    outboxRepository.save(linha);
                });
    }
}
