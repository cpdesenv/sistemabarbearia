package com.barbearia.agenda.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalvarAgendamentoRequest(

        @NotNull(message = "O cliente e obrigatorio.")
        UUID clienteUuid,

        @NotNull(message = "O profissional e obrigatorio.")
        UUID profissionalUuid,

        @NotEmpty(message = "Selecione ao menos um servico.")
        List<UUID> servicoUuids,

        @NotNull(message = "O horario de inicio e obrigatorio.")
        Instant inicio,

        @Size(max = 2000, message = "A observacao deve ter no maximo 2000 caracteres.")
        String observacao) {
}
