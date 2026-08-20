package com.barbearia.cliente.dto;

import java.util.UUID;

/** Representacao minima usada quando so o suficiente para identificar o cliente e necessario. */
public record ClienteResumoDto(UUID uuid, String nome, String telefone) {
}
