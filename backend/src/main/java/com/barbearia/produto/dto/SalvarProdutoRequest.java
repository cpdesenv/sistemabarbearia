package com.barbearia.produto.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalvarProdutoRequest(

        @NotBlank(message = "O nome e obrigatorio.")
        @Size(max = 255, message = "O nome deve ter no maximo 255 caracteres.")
        String nome,

        @Size(max = 2000, message = "A descricao deve ter no maximo 2000 caracteres.")
        String descricao,

        @Size(max = 100, message = "A categoria deve ter no maximo 100 caracteres.")
        String categoria,

        @Size(max = 20, message = "A unidade deve ter no maximo 20 caracteres.")
        String unidade,

        @NotNull(message = "O preco de venda e obrigatorio.")
        @DecimalMin(value = "0.00", message = "O preco de venda nao pode ser negativo.")
        BigDecimal precoVenda,

        @DecimalMin(value = "0.00", message = "O preco de custo nao pode ser negativo.")
        BigDecimal precoCusto,

        @NotNull(message = "O estoque minimo e obrigatorio.")
        @Min(value = 0, message = "O estoque minimo nao pode ser negativo.")
        Integer estoqueMinimo) {
}
