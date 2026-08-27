package com.barbearia.cliente.dto;

import java.util.List;

import com.barbearia.agenda.dto.AgendamentoDto;
import com.barbearia.financeiro.dto.ComandaDto;
import com.barbearia.fiscal.dto.ComprovanteDto;

/**
 * Ficha consultada pelo barbeiro antes do atendimento: dados do cliente mais
 * o historico de agendamentos, comandas/atendimentos e comprovantes/notas
 * emitidos.
 */
public record FichaClienteDto(
        ClienteDto cliente,
        List<AgendamentoDto> agendamentos,
        List<ComandaDto> atendimentos,
        List<ComprovanteDto> notasFiscais) {
}
