package com.barbearia.portal.dto;

import java.util.UUID;

/** Visao publica de um profissional — sem contato, comissao ou dados internos. */
public record PortalProfissionalDto(
        UUID uuid,
        String nome,
        String corAgenda) {
}
