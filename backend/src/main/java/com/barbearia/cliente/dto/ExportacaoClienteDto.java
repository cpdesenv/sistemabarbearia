package com.barbearia.cliente.dto;

import java.time.Instant;

/** Payload de portabilidade de dados (LGPD, art. 18): tudo o que o sistema guarda sobre o cliente. */
public record ExportacaoClienteDto(Instant exportadoEm, ClienteDto dados) {
}
