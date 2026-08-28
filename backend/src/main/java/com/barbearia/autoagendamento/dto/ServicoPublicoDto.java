package com.barbearia.autoagendamento.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ServicoPublicoDto(UUID uuid, String nome, String descricao, String categoria, BigDecimal preco,
        int duracaoMinutos) {
}
