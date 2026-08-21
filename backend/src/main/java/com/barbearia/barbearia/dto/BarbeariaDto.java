package com.barbearia.barbearia.dto;

import java.time.Instant;

public record BarbeariaDto(
        String nome,
        String cnpj,
        String telefone,
        String email,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf,
        String cep,
        String fusoHorario,
        int antecedenciaMinimaAgendamentoMinutos,
        int antecedenciaMaximaAgendamentoDias,
        int antecedenciaMinimaCancelamentoMinutos,
        int granularidadeSlotMinutos,
        Instant atualizadoEm) {
}
