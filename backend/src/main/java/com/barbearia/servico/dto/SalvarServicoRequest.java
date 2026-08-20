package com.barbearia.servico.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalvarServicoRequest(

        @NotBlank(message = "O nome e obrigatorio.")
        @Size(max = 150, message = "O nome deve ter no maximo 150 caracteres.")
        String nome,

        @Size(max = 2000, message = "A descricao deve ter no maximo 2000 caracteres.")
        String descricao,

        @Size(max = 80, message = "A categoria deve ter no maximo 80 caracteres.")
        String categoria,

        @NotNull(message = "O preco e obrigatorio.")
        @DecimalMin(value = "0.01", message = "O preco deve ser maior que zero.")
        BigDecimal preco,

        @NotNull(message = "A duracao e obrigatoria.")
        @Min(value = 1, message = "A duracao deve ser de pelo menos 1 minuto.")
        Integer duracaoMinutos) {
}
