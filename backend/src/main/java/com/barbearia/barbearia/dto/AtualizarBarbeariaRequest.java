package com.barbearia.barbearia.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizarBarbeariaRequest(

        @NotBlank(message = "O nome e obrigatorio.")
        @Size(max = 150, message = "O nome deve ter no maximo 150 caracteres.")
        String nome,

        @Size(max = 18, message = "O CNPJ deve ter no maximo 18 caracteres.")
        String cnpj,

        @Size(max = 20, message = "O telefone deve ter no maximo 20 caracteres.")
        String telefone,

        @Email(message = "Informe um e-mail valido.")
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

        @Size(min = 2, max = 2, message = "A UF deve ter exatamente 2 letras.")
        String uf,

        @Size(max = 9, message = "O CEP deve ter no maximo 9 caracteres.")
        String cep,

        @NotBlank(message = "O fuso horario e obrigatorio.")
        @Size(max = 50, message = "O fuso horario deve ter no maximo 50 caracteres.")
        String fusoHorario,

        @Min(value = 0, message = "A antecedencia minima de agendamento nao pode ser negativa.")
        int antecedenciaMinimaAgendamentoMinutos,

        @Min(value = 1, message = "A antecedencia maxima de agendamento deve ser de pelo menos 1 dia.")
        int antecedenciaMaximaAgendamentoDias,

        @Min(value = 0, message = "A antecedencia minima de cancelamento nao pode ser negativa.")
        int antecedenciaMinimaCancelamentoMinutos,

        @Min(value = 1, message = "A granularidade de slot deve ser de pelo menos 1 minuto.")
        int granularidadeSlotMinutos,

        boolean portalAutoagendamentoAtivo) {
}
