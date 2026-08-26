package com.barbearia.assinatura.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalvarPlanoAssinaturaRequest(

        @NotBlank(message = "O nome e obrigatorio.")
        @Size(max = 150, message = "O nome deve ter no maximo 150 caracteres.")
        String nome,

        @Size(max = 2000, message = "A descricao deve ter no maximo 2000 caracteres.")
        String descricao,

        @NotNull(message = "O preco mensal e obrigatorio.")
        @DecimalMin(value = "0.01", message = "O preco mensal deve ser maior que zero.")
        BigDecimal precoMensal,

        @NotNull(message = "A quantidade de cortes por ciclo e obrigatoria.")
        @Min(value = 1, message = "O plano deve incluir ao menos 1 corte por ciclo.")
        Integer cortesIncluidosPorCiclo,

        @NotNull(message = "O percentual de desconto adicional e obrigatorio.")
        @DecimalMin(value = "0.00", message = "O percentual de desconto nao pode ser negativo.")
        @DecimalMax(value = "100.00", message = "O percentual de desconto nao pode passar de 100.")
        BigDecimal percentualDescontoAdicional,

        @NotEmpty(message = "Selecione ao menos um servico incluso no plano.")
        List<UUID> servicosInclusosUuids) {
}
