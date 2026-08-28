package com.barbearia.ia.gateway;

import java.util.List;

/**
 * Fronteira com o provedor de LLM. O {@code AgenteAtendimentoService} e o
 * dono do loop de tool-calling: a cada chamada aqui, envia o historico
 * completo e recebe OU o texto final do turno OU pedidos de tool_use para
 * executar e devolver via um novo turno {@code ResultadosFerramenta}.
 */
public interface AiAgentGateway {

    /**
     * @param chaveConversa identificador estavel da conversa (telefone E.164 normalizado) — usado pelo
     *                       {@code MockAiAgentGateway} para rotear roteiros programados por teste, sem estado
     *                       global compartilhado entre testes.
     */
    RespostaAgenteIa processarTurno(String chaveConversa, List<TurnoConversa> historico,
            List<DefinicaoFerramentaIa> ferramentas, String systemPrompt);

    /** Identificador do modelo usado nesta chamada, para registrar em {@code UsoLlm.modelo}. */
    String modelo();
}
