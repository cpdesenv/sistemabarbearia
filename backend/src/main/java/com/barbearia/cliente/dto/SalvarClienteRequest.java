package com.barbearia.cliente.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public record SalvarClienteRequest(

        @NotBlank(message = "O nome e obrigatorio.")
        @Size(max = 150, message = "O nome deve ter no maximo 150 caracteres.")
        String nome,

        @NotBlank(message = "O telefone e obrigatorio.")
        String telefone,

        String whatsapp,

        String cpf,

        @Email(message = "O e-mail informado e invalido.")
        @Size(max = 180, message = "O e-mail deve ter no maximo 180 caracteres.")
        String email,

        @Size(max = 200, message = "O logradouro deve ter no maximo 200 caracteres.")
        String logradouro,

        @Size(max = 20, message = "O numero deve ter no maximo 20 caracteres.")
        String numero,

        @Size(max = 100, message = "O complemento deve ter no maximo 100 caracteres.")
        String complemento,

        @Size(max = 100, message = "O bairro deve ter no maximo 100 caracteres.")
        String bairro,

        @Size(max = 100, message = "A cidade deve ter no maximo 100 caracteres.")
        String cidade,

        @Size(max = 2, message = "A UF deve ter 2 caracteres.")
        String uf,

        @Size(max = 9, message = "O CEP deve ter no maximo 9 caracteres.")
        String cep,

        @Past(message = "A data de nascimento deve estar no passado.")
        LocalDate dataNascimento,

        @Size(max = 2000, message = "As observacoes devem ter no maximo 2000 caracteres.")
        String observacoes,

        boolean optInWhatsapp,

        boolean consentimentoLgpd) {
}
