package com.barbearia.agenda.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AgendamentoServicoDto(UUID servicoUuid, String nome, int duracaoMinutos, BigDecimal preco) {
}
