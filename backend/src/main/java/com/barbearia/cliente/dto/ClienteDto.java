package com.barbearia.cliente.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.barbearia.cliente.domain.OrigemCadastro;

public record ClienteDto(
        UUID uuid,
        String nome,
        String telefone,
        String whatsapp,
        String cpf,
        String email,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf,
        String cep,
        LocalDate dataNascimento,
        String observacoes,
        boolean optInWhatsapp,
        OrigemCadastro origemCadastro,
        boolean consentimentoLgpd,
        Instant consentimentoLgpdEm,
        boolean anonimizado,
        Instant criadoEm,
        Instant atualizadoEm) {
}
