package com.barbearia.profissional.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SalvarProfissionalRequest(

        @NotBlank(message = "O nome e obrigatorio.")
        @Size(max = 150, message = "O nome deve ter no maximo 150 caracteres.")
        String nome,

        @Email(message = "Informe um e-mail valido.")
        @Size(max = 180, message = "O e-mail deve ter no maximo 180 caracteres.")
        String email,

        @Size(max = 20, message = "O telefone deve ter no maximo 20 caracteres.")
        String telefone,

        @NotBlank(message = "A cor da agenda e obrigatoria.")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Informe uma cor valida no formato hexadecimal (#RRGGBB).")
        String corAgenda,

        @NotNull(message = "A comissao padrao e obrigatoria.")
        @DecimalMin(value = "0", message = "A comissao nao pode ser negativa.")
        @DecimalMax(value = "100", message = "A comissao nao pode ser maior que 100%.")
        BigDecimal comissaoPercentualPadrao) {
}
