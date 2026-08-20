package com.barbearia.profissional.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProfissionalDto(
        UUID uuid,
        String nome,
        String email,
        String telefone,
        String corAgenda,
        BigDecimal comissaoPercentualPadrao,
        boolean ativo,
        Instant criadoEm,
        Instant atualizadoEm) {
}
