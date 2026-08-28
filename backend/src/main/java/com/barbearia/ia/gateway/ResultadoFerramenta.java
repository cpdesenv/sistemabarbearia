package com.barbearia.ia.gateway;

/** Resultado de uma {@link ChamadaFerramenta}, associado pelo mesmo {@code chamadaId}. {@code erro=true} sinaliza falha ao LLM. */
public record ResultadoFerramenta(String chamadaId, String conteudo, boolean erro) {
}
