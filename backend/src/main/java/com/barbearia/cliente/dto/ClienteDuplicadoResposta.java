package com.barbearia.cliente.dto;

import java.time.Instant;

/** Corpo de erro 409 quando ja existe cliente cadastrado com o mesmo telefone. */
public record ClienteDuplicadoResposta(
        Instant timestamp,
        int status,
        String erro,
        String mensagem,
        ClienteResumoDto clienteExistente) {
}
