package com.barbearia.ia.gateway;

import java.util.List;
import java.util.Map;

/**
 * Schema de uma tool exposta ao agente de IA, em formato neutro (JSON
 * Schema simplificado — nome da propriedade para sua descricao/tipo). O
 * {@code AiAgentGatewayReal} traduz para {@code Tool} do SDK da Anthropic; o
 * {@code MockAiAgentGateway} so usa {@link #nome} para rotear respostas
 * programadas.
 */
public record DefinicaoFerramentaIa(String nome, String descricao, Map<String, Object> propriedades,
        List<String> obrigatorias) {
}
