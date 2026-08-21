package com.barbearia.financeiro.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.barbearia.financeiro.domain.FormaPagamento;
import com.barbearia.financeiro.domain.StatusComanda;

public record ComandaDto(
        UUID uuid,
        UUID agendamentoUuid,
        UUID clienteUuid,
        String clienteNome,
        UUID profissionalUuid,
        String profissionalNome,
        StatusComanda status,
        List<ComandaItemDto> itens,
        BigDecimal descontoValor,
        String descontoMotivo,
        FormaPagamento formaPagamento,
        BigDecimal subtotal,
        BigDecimal valorTotal,
        BigDecimal comissaoTotal,
        Instant fechadaEm,
        Instant estornadaEm,
        String motivoEstorno,
        Instant criadoEm,
        Instant atualizadoEm) {
}
