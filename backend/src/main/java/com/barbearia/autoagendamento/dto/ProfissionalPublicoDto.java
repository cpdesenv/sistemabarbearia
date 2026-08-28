package com.barbearia.autoagendamento.dto;

import java.util.UUID;

/**
 * Projecao publica de profissional — deliberadamente sem e-mail, telefone
 * nem comissao (dados internos de {@code ProfissionalDto}, sem motivo pra
 * chegar numa pagina sem autenticacao).
 */
public record ProfissionalPublicoDto(UUID uuid, String nome, String corAgenda) {
}
