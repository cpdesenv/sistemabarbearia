package com.barbearia.mensageria.dev;

import jakarta.validation.constraints.NotBlank;

public record SimularMensagemInboundRequest(

        @NotBlank(message = "O telefone e obrigatorio.")
        String telefone,

        @NotBlank(message = "O texto e obrigatorio.")
        String texto) {
}
