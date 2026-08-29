package com.barbearia.portal.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PortalAgendamentoRequest(

        @NotBlank(message = "O nome e obrigatorio.")
        @Size(max = 150, message = "O nome deve ter no maximo 150 caracteres.")
        String nome,

        @NotBlank(message = "O telefone e obrigatorio.")
        String telefone,

        @Email(message = "O e-mail informado e invalido.")
        @Size(max = 180, message = "O e-mail deve ter no maximo 180 caracteres.")
        String email,

        @NotNull(message = "O profissional e obrigatorio.")
        UUID profissionalUuid,

        @NotEmpty(message = "Selecione ao menos um servico.")
        List<UUID> servicoUuids,

        @NotNull(message = "O horario de inicio e obrigatorio.")
        Instant inicio,

        boolean consentimentoLgpd) {

    @AssertTrue(message = "E necessario aceitar o consentimento LGPD para agendar pelo portal.")
    public boolean isConsentimentoLgpdAceito() {
        return consentimentoLgpd;
    }
}
