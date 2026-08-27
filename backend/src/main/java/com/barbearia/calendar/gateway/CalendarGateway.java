package com.barbearia.calendar.gateway;

import com.barbearia.agenda.domain.Agendamento;

/**
 * Criacao/atualizacao/remocao de evento no Google Calendar. A implementacao
 * mock ({@code MockCalendarGateway}, padrao) e usada em dev/teste sem
 * nenhuma credencial Google; a implementacao real
 * ({@code GoogleCalendarGateway}) e ativada com {@code app.calendar.gateway=google}.
 *
 * <p>{@code atualizarEvento}/{@code removerEvento} usam sempre
 * {@link Agendamento#getGoogleCalendarId()}/{@link Agendamento#getGoogleEventId()}
 * ja persistidos (setados quando {@link #criarEvento} teve sucesso), nunca
 * resolvendo o calendario de novo — assim o evento sempre e atualizado/removido
 * no mesmo calendario onde foi criado, mesmo que o profissional do agendamento
 * mude depois numa remarcacao.
 */
public interface CalendarGateway {

    EventoCriadoResultado criarEvento(Agendamento agendamento);

    void atualizarEvento(Agendamento agendamento);

    void removerEvento(Agendamento agendamento);
}
