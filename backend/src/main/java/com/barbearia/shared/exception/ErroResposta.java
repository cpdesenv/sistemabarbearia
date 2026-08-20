package com.barbearia.shared.exception;

import java.time.Instant;
import java.util.List;

public record ErroResposta(
        Instant timestamp,
        int status,
        String erro,
        String mensagem,
        String caminho,
        List<ErroCampo> campos) {

    public static ErroResposta de(int status, String erro, String mensagem, String caminho) {
        return new ErroResposta(Instant.now(), status, erro, mensagem, caminho, null);
    }

    public static ErroResposta deValidacao(String mensagem, String caminho, List<ErroCampo> campos) {
        return new ErroResposta(Instant.now(), 400, "REQUISICAO_INVALIDA", mensagem, caminho, campos);
    }
}
