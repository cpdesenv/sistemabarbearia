package com.barbearia.portal.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PortalAgendamentoServicoDto(UUID servicoUuid, String nome, int duracaoMinutos, BigDecimal preco) {
}
