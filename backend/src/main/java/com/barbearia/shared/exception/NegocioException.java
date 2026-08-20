package com.barbearia.shared.exception;

/** Violacao de regra de negocio — vira HTTP 400 com mensagem legivel ao usuario. */
public class NegocioException extends RuntimeException {

    public NegocioException(String mensagem) {
        super(mensagem);
    }
}
