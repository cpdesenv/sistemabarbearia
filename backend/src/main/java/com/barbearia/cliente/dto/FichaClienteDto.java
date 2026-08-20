package com.barbearia.cliente.dto;

import java.util.List;

/**
 * Ficha consultada pelo barbeiro antes do atendimento: dados do cliente mais
 * o historico. Nesta fase o historico esta sempre vazio porque agendamento
 * (Fase 4), comanda (Fase 5) e nota fiscal (Fase 6) ainda nao existem — a
 * estrutura ja fica pronta para ser populada quando essas fases chegarem.
 */
public record FichaClienteDto(
        ClienteDto cliente,
        List<Object> agendamentos, // TODO(fase-4): popular com o historico de agendamentos do cliente.
        List<Object> atendimentos, // TODO(fase-5): popular com o historico de comandas/atendimentos.
        List<Object> notasFiscais) { // TODO(fase-6): popular com os comprovantes/notas emitidos.
}
