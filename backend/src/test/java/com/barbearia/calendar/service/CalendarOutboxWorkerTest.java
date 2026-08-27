package com.barbearia.calendar.service;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.calendar.domain.AgendamentoCalendarOutbox;
import com.barbearia.calendar.domain.StatusOutbox;
import com.barbearia.calendar.domain.TipoOperacaoOutbox;
import com.barbearia.calendar.gateway.CalendarGateway;
import com.barbearia.calendar.gateway.CalendarSyncException;
import com.barbearia.calendar.gateway.EventoCriadoResultado;
import com.barbearia.calendar.repository.AgendamentoCalendarOutboxRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarOutboxWorkerTest {

    @Mock
    private AgendamentoCalendarOutboxRepository outboxRepository;
    @Mock
    private AgendamentoRepository agendamentoRepository;
    @Mock
    private CalendarGateway calendarGateway;
    @Mock
    private IntegracaoGoogleCalendarService integracaoService;

    private CalendarOutboxWorker worker;

    @BeforeEach
    void montarWorker() {
        worker = new CalendarOutboxWorker(outboxRepository, agendamentoRepository, calendarGateway,
                integracaoService);
    }

    @Test
    void deveCriarEventoEMarcarOutboxComoConcluido() {
        Agendamento agendamento = new Agendamento();
        AgendamentoCalendarOutbox linha = linhaPendente(agendamento, TipoOperacaoOutbox.CRIAR);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(linha));
        when(calendarGateway.criarEvento(agendamento))
                .thenReturn(new EventoCriadoResultado("evento-123", "calendario-abc"));

        worker.processarUm(1L);

        assertThat(agendamento.getGoogleEventId()).isEqualTo("evento-123");
        assertThat(agendamento.getGoogleCalendarId()).isEqualTo("calendario-abc");
        assertThat(linha.getStatus()).isEqualTo(StatusOutbox.CONCLUIDO);
        verify(agendamentoRepository).save(agendamento);
    }

    @Test
    void deveRemoverEventoELimparIdsDoAgendamento() {
        Agendamento agendamento = new Agendamento();
        agendamento.setGoogleEventId("evento-123");
        agendamento.setGoogleCalendarId("calendario-abc");
        AgendamentoCalendarOutbox linha = linhaPendente(agendamento, TipoOperacaoOutbox.REMOVER);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(linha));

        worker.processarUm(1L);

        assertThat(agendamento.getGoogleEventId()).isNull();
        assertThat(agendamento.getGoogleCalendarId()).isNull();
        assertThat(linha.getStatus()).isEqualTo(StatusOutbox.CONCLUIDO);
    }

    @Test
    void falhaDeveIncrementarTentativasEAgendarBackoffSemDesistir() {
        Agendamento agendamento = new Agendamento();
        AgendamentoCalendarOutbox linha = linhaPendente(agendamento, TipoOperacaoOutbox.CRIAR);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(linha));
        when(calendarGateway.criarEvento(any()))
                .thenThrow(new CalendarSyncException("Falha simulada de rede", new RuntimeException()));

        worker.processarUm(1L);

        assertThat(linha.getStatus()).isEqualTo(StatusOutbox.PENDENTE);
        assertThat(linha.getTentativas()).isEqualTo(1);
        assertThat(linha.getUltimoErro()).isEqualTo("Falha simulada de rede");
        assertThat(linha.getProximaTentativaEm()).isAfter(Instant.now());
        verify(integracaoService).registrarUltimoErro("Falha simulada de rede");
    }

    @Test
    void deveMarcarFalhaPermanenteAposEsgotarTentativas() {
        Agendamento agendamento = new Agendamento();
        AgendamentoCalendarOutbox linha = linhaPendente(agendamento, TipoOperacaoOutbox.CRIAR);
        linha.setTentativas(7);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(linha));
        when(calendarGateway.criarEvento(any()))
                .thenThrow(new CalendarSyncException("Falha persistente", new RuntimeException()));

        worker.processarUm(1L);

        assertThat(linha.getTentativas()).isEqualTo(8);
        assertThat(linha.getStatus()).isEqualTo(StatusOutbox.FALHA_PERMANENTE);
    }

    @Test
    void naoDeveReprocessarLinhaQueNaoEstaMaisPendente() {
        Agendamento agendamento = new Agendamento();
        AgendamentoCalendarOutbox linha = linhaPendente(agendamento, TipoOperacaoOutbox.CRIAR);
        linha.setStatus(StatusOutbox.CONCLUIDO);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(linha));

        worker.processarUm(1L);

        verify(calendarGateway, never()).criarEvento(any());
    }

    private AgendamentoCalendarOutbox linhaPendente(Agendamento agendamento, TipoOperacaoOutbox tipo) {
        AgendamentoCalendarOutbox linha = new AgendamentoCalendarOutbox();
        linha.setId(1L);
        linha.setAgendamento(agendamento);
        linha.setTipoOperacao(tipo);
        linha.setStatus(StatusOutbox.PENDENTE);
        return linha;
    }
}
