package com.barbearia.shared.exception;

/** Login ou refresh token invalidos — vira HTTP 401. */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException(String mensagem) {
        super(mensagem);
    }
}
