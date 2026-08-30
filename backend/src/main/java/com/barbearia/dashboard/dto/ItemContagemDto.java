package com.barbearia.dashboard.dto;

/** Um rotulo (servico ou profissional) e a contagem associada, usado nos graficos de barras do dashboard. */
public record ItemContagemDto(String nome, long quantidade) {
}
