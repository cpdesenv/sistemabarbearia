package com.barbearia.cliente.dto;

import jakarta.validation.constraints.NotBlank;

public record AnonimizarClienteRequest(
        @NotBlank(message = "O motivo e obrigatorio.") String motivo) {
}
