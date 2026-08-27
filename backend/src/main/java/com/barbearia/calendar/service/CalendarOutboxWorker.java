package com.barbearia.calendar.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.calendar.domain.AgendamentoCalendarOutbox;
import com.barbearia.calendar.domain.StatusOutbox;
import com.barbearia.calendar.gateway.CalendarGateway;
import com.barbearia.calendar.gateway.EventoCriadoResultado;
import com.barbearia.calendar.repository.AgendamentoCalendarOutboxRepository;

/**
 * Processa a fila de sincronizacao com o Google Calendar (padrao outbox).
 * Roda no mesmo estilo de {@code AssinaturaRenovacaoScheduler}: um
 * {@code @Scheduled} simples, sem infraestrutura de fila externa — o
 * "enfileirar" e so uma linha na tabela {@code agendamento_calendar_outbox},
 * ja commitada pela transacao do agendamento.
 */
@Component
public class CalendarOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(CalendarOutboxWorker.class);

    private static final int MAX_TENTATIVAS = 8;
    private static final Duration[] BACKOFF = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(30),
            Duration.ofHours(1)
    };

    private final AgendamentoCalendarOutboxRepository outboxRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final CalendarGateway calendarGateway;
    private final IntegracaoGoogleCalendarService integracaoService;

    /**
     * Referencia ao proprio bean (proxy), injetada de forma preguicosa —
     * necessaria porque chamar processarUm(id) diretamente (this.processarUm)
     * de dentro de processarPendencias() NAO passa pelo proxy do Spring, e
     * por isso @Transactional seria ignorado nessa chamada interna (o
     * classico "self-invocation problem" documentado pelo proprio Spring).
     */
    private final CalendarOutboxWorker self;

    public CalendarOutboxWorker(AgendamentoCalendarOutboxRepository outboxRepository,
            AgendamentoRepository agendamentoRepository, CalendarGateway calendarGateway,
            IntegracaoGoogleCalendarService integracaoService, @Lazy CalendarOutboxWorker self) {
        this.outboxRepository = outboxRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.calendarGateway = calendarGateway;
        this.integracaoService = integracaoService;
        this.self = self;
    }

    @Scheduled(fixedDelayString = "${app.calendar.outbox-intervalo-ms:30000}")
    public void processarPendencias() {
        List<Long> idsPendentes = outboxRepository
                .findByStatusAndProximaTentativaEmLessThanEqualOrderByProximaTentativaEmAsc(StatusOutbox.PENDENTE,
                        Instant.now())
                .stream()
                .map(AgendamentoCalendarOutbox::getId)
                .toList();

        for (Long id : idsPendentes) {
            self.processarUm(id);
        }
    }

    @Transactional
    public void processarUm(Long outboxId) {
        AgendamentoCalendarOutbox linha = outboxRepository.findById(outboxId).orElse(null);
        if (linha == null || linha.getStatus() != StatusOutbox.PENDENTE) {
            return;
        }

        Agendamento agendamento = linha.getAgendamento();
        try {
            switch (linha.getTipoOperacao()) {
                case CRIAR -> {
                    EventoCriadoResultado resultado = calendarGateway.criarEvento(agendamento);
                    agendamento.setGoogleEventId(resultado.googleEventId());
                    agendamento.setGoogleCalendarId(resultado.googleCalendarId());
                    agendamentoRepository.save(agendamento);
                }
                case ATUALIZAR -> calendarGateway.atualizarEvento(agendamento);
                case REMOVER -> {
                    calendarGateway.removerEvento(agendamento);
                    agendamento.setGoogleEventId(null);
                    agendamento.setGoogleCalendarId(null);
                    agendamentoRepository.save(agendamento);
                }
            }
            linha.setStatus(StatusOutbox.CONCLUIDO);
            linha.setUltimoErro(null);
            outboxRepository.save(linha);
        } catch (Exception e) {
            registrarFalha(linha, agendamento, e);
        }
    }

    private void registrarFalha(AgendamentoCalendarOutbox linha, Agendamento agendamento, Exception e) {
        int tentativas = linha.getTentativas() + 1;
        String mensagem = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

        linha.setTentativas(tentativas);
        linha.setUltimoErro(mensagem);
        if (tentativas >= MAX_TENTATIVAS) {
            linha.setStatus(StatusOutbox.FALHA_PERMANENTE);
        } else {
            linha.setProximaTentativaEm(Instant.now().plus(calcularBackoff(tentativas)));
        }
        outboxRepository.save(linha);
        integracaoService.registrarUltimoErro(mensagem);

        log.warn("Falha ao sincronizar agendamento {} com o Google Calendar (tentativa {}/{}): {}",
                agendamento.getUuidPublico(), tentativas, MAX_TENTATIVAS, mensagem);
    }

    private Duration calcularBackoff(int tentativas) {
        int indice = Math.min(tentativas - 1, BACKOFF.length - 1);
        return BACKOFF[indice];
    }
}
