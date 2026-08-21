package com.barbearia.produto.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AjusteEstoqueRequest(

        @NotNull(message = "A quantidade contada e obrigatoria.")
        @Min(value = 0, message = "A quantidade contada nao pode ser negativa.")
        Integer novaQuantidadeContada,

        @NotBlank(message = "O motivo do ajuste e obrigatorio.")
        String motivo) {
}
