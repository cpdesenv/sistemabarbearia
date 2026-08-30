package com.barbearia.dashboard.dto;

/**
 * {@code cancelamentosMes} e {@code faltasMes} contam agendamentos cujo
 * {@code inicio} cai no mes corrente (nao quando a acao de cancelar/marcar
 * falta foi executada). {@code agendamentosForaDeSincronia} e' a contagem de
 * entradas do outbox do Google Calendar em FALHA_PERMANENTE — ver
 * {@code AgendamentoCalendarOutbox}.
 */
public record IndicadoresSaudeDto(
        long clientesNovosMes,
        long cancelamentosMes,
        long faltasMes,
        long agendamentosForaDeSincronia) {
}
