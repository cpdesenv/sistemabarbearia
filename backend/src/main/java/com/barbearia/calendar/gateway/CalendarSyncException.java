package com.barbearia.calendar.gateway;

/**
 * Falha ao chamar o Google Calendar (rede, credencial revogada, etc).
 * Capturada pelo {@code CalendarOutboxWorker}, nunca pelo fluxo de
 * confirmar/remarcar/cancelar agendamento — e por isso que existe o outbox.
 */
public class CalendarSyncException extends RuntimeException {

    public CalendarSyncException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
