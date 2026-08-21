package com.barbearia.agenda.exception;

/** Horario acabou de ser ocupado por outra requisicao concorrente — vira HTTP 409. */
public class ConflitoAgendamentoException extends RuntimeException {

    public ConflitoAgendamentoException(String mensagem) {
        super(mensagem);
    }
}
