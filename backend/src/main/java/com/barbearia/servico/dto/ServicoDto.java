package com.barbearia.servico.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ServicoDto(
        UUID uuid,
        String nome,
        String descricao,
        String categoria,
        BigDecimal preco,
        int duracaoMinutos,
        boolean ativo,
        Instant criadoEm,
        Instant atualizadoEm) {
}
