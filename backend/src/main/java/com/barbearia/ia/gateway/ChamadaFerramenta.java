package com.barbearia.ia.gateway;

import java.util.Map;

/** Um pedido do LLM para executar uma tool. {@link #id} correlaciona com o {@link ResultadoFerramenta} devolvido. */
public record ChamadaFerramenta(String id, String nome, Map<String, Object> entrada) {
}
