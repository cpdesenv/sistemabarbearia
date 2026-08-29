package com.barbearia.portal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.barbearia.agenda.domain.StatusAgendamento;

/**
 * Visao publica do agendamento recem-criado pelo portal. Devolve o nome
 * digitado pelo proprio solicitante (nao o nome gravado no cadastro do
 * cliente) e omite {@code clienteUuid}/{@code clienteTelefone} — quando o
 * telefone informado ja pertence a um cliente cadastrado, o agendamento e'
 * vinculado a esse cliente (evita duplicidade), mas devolver o nome/telefone
 * armazenados a um chamador anonimo funcionaria como oraculo de dados
 * pessoais de terceiros (confirma que aquele telefone e' de um cliente e
 * revela o nome verdadeiro dele).
 */
public record PortalAgendamentoConfirmadoDto(
        UUID uuid,
        String clienteNome,
        String profissionalNome,
        String profissionalCorAgenda,
        List<PortalAgendamentoServicoDto> servicos,
        Instant inicio,
        Instant fim,
        BigDecimal valorTotal,
        StatusAgendamento status) {
}
