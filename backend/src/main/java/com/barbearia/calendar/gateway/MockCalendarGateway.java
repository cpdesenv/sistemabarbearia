package com.barbearia.calendar.gateway;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.barbearia.agenda.domain.Agendamento;

/**
 * Simula a criacao/atualizacao/remocao de evento sem chamar o Google — usada
 * em dev e na suite de testes, sem nenhuma credencial. Padrao quando
 * {@code app.calendar.gateway} nao esta configurado.
 */
@Component
@ConditionalOnProperty(prefix = "app.calendar", name = "gateway", havingValue = "mock", matchIfMissing = true)
public class MockCalendarGateway implements CalendarGateway {

    private static final Logger log = LoggerFactory.getLogger(MockCalendarGateway.class);
    private static final String CALENDARIO_MOCK = "mock-calendario";

    @Override
    public EventoCriadoResultado criarEvento(Agendamento agendamento) {
        String eventoId = "mock-evento-" + UUID.randomUUID();
        log.info("[MOCK CALENDAR] Evento '{}' criado para o agendamento {}", eventoId, agendamento.getUuidPublico());
        return new EventoCriadoResultado(eventoId, CALENDARIO_MOCK);
    }

    @Override
    public void atualizarEvento(Agendamento agendamento) {
        log.info("[MOCK CALENDAR] Evento '{}' atualizado para o agendamento {}", agendamento.getGoogleEventId(),
                agendamento.getUuidPublico());
    }

    @Override
    public void removerEvento(Agendamento agendamento) {
        log.info("[MOCK CALENDAR] Evento '{}' removido (agendamento {})", agendamento.getGoogleEventId(),
                agendamento.getUuidPublico());
    }
}
