package com.barbearia.ia.gateway;

import java.util.List;

/**
 * Um turno do historico de conversa enviado ao LLM, em formato neutro (sem
 * tipos do SDK da Anthropic) — o {@code AiAgentGatewayReal} e' o unico ponto
 * que traduz isso para {@code MessageParam}/{@code ContentBlockParam}.
 */
public sealed interface TurnoConversa {

    record MensagemUsuario(String texto) implements TurnoConversa {
    }

    record MensagemAssistente(String texto, List<ChamadaFerramenta> chamadasFerramenta) implements TurnoConversa {
    }

    record ResultadosFerramenta(List<ResultadoFerramenta> resultados) implements TurnoConversa {
    }
}
