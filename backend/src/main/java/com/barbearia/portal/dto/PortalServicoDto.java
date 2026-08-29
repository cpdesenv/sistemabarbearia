package com.barbearia.portal.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Visao publica de um servico — sem os campos internos (custo, ativo, etc). */
public record PortalServicoDto(
        UUID uuid,
        String nome,
        String descricao,
        String categoria,
        BigDecimal preco,
        int duracaoMinutos) {
}
