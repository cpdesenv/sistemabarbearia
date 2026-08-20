package com.barbearia.cliente.exception;

import com.barbearia.cliente.domain.Cliente;

/** Ja existe cliente cadastrado com o mesmo telefone — vira HTTP 409. */
public class ClienteDuplicadoException extends RuntimeException {

    private final Cliente clienteExistente;

    public ClienteDuplicadoException(Cliente clienteExistente) {
        super("Ja existe um cliente cadastrado com este telefone.");
        this.clienteExistente = clienteExistente;
    }

    public Cliente getClienteExistente() {
        return clienteExistente;
    }
}
