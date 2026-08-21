package com.barbearia.produto.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProdutoDto(
        UUID uuid,
        String nome,
        String descricao,
        String categoria,
        String unidade,
        BigDecimal precoVenda,
        BigDecimal precoCusto,
        int estoqueMinimo,
        int estoqueAtual,
        boolean ativo,
        Instant criadoEm,
        Instant atualizadoEm) {
}
