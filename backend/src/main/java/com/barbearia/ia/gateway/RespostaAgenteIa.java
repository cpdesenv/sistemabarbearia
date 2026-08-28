package com.barbearia.ia.gateway;

import java.util.List;

/** Resposta de uma chamada ao {@link AiAgentGateway}: texto final OU pedidos de tool_use, nunca os dois com sentido simultaneo. */
public record RespostaAgenteIa(String textoFinal, List<ChamadaFerramenta> chamadasFerramenta, int tokensEntrada,
        int tokensSaida) {

    public boolean requerExecucaoDeFerramentas() {
        return chamadasFerramenta != null && !chamadasFerramenta.isEmpty();
    }
}
