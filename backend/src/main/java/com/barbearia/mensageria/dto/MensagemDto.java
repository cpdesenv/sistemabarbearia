package com.barbearia.mensageria.dto;

import java.time.Instant;
import java.util.UUID;

import com.barbearia.mensageria.domain.DirecaoMensagem;
import com.barbearia.mensageria.domain.StatusMensagem;
import com.barbearia.mensageria.domain.TipoMensagem;

public record MensagemDto(
        UUID uuid,
        DirecaoMensagem direcao,
        TipoMensagem tipo,
        String conteudo,
        StatusMensagem status,
        Instant criadoEm) {
}
