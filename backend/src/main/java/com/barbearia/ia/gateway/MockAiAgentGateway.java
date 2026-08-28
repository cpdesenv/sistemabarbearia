package com.barbearia.ia.gateway;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LLM mockado deterministico — usado em dev e na suite de diálogos-roteiro
 * do CI (ver PRD, Fase 10), nunca chama nenhum provedor real.
 *
 * <p>Cada teste programa, via {@link #programar}, a sequencia EXATA de
 * respostas que o "modelo" deve devolver para uma dada {@code chaveConversa}
 * (o telefone da conversa) — sem isso nao haveria como testar
 * deterministicamente um comportamento que, no provedor real, e' decidido
 * pelo LLM. Chaves diferentes tem filas independentes, entao testes
 * concorrentes com telefones unicos nunca compartilham estado (mesma licao
 * da Fase 9: nunca reusar uma chave fixa entre classes de teste).
 *
 * <p>Quando nenhum roteiro foi programado para a chave (uso manual via
 * simulador do painel, em dev), cai numa resposta fixa de boas-vindas — nao
 * e' um simulador "inteligente", so evita erro 500 ao testar a fiacao sem
 * escrever um roteiro.
 */
@Component
@ConditionalOnProperty(prefix = "app.ia", name = "gateway", havingValue = "mock", matchIfMissing = true)
public class MockAiAgentGateway implements AiAgentGateway {

    private static final RespostaAgenteIa RESPOSTA_PADRAO = new RespostaAgenteIa(
            "Oi! Sou o assistente da barbearia (modo mock, sem roteiro programado para esta conversa). "
                    + "Em que posso ajudar?",
            List.of(), 0, 0);

    private final Map<String, Queue<RespostaAgenteIa>> roteiros = new ConcurrentHashMap<>();

    /** Enfileira as respostas exatas que o mock deve devolver, em ordem, para a chave informada. */
    public void programar(String chaveConversa, RespostaAgenteIa... respostas) {
        roteiros.computeIfAbsent(chaveConversa, k -> new ConcurrentLinkedQueue<>()).addAll(List.of(respostas));
    }

    /** Remove qualquer roteiro programado para a chave — chamar no @AfterEach dos testes que usam {@link #programar}. */
    public void limpar(String chaveConversa) {
        roteiros.remove(chaveConversa);
    }

    @Override
    public RespostaAgenteIa processarTurno(String chaveConversa, List<TurnoConversa> historico,
            List<DefinicaoFerramentaIa> ferramentas, String systemPrompt) {
        Queue<RespostaAgenteIa> fila = roteiros.get(chaveConversa);
        if (fila != null && !fila.isEmpty()) {
            return fila.poll();
        }
        return RESPOSTA_PADRAO;
    }

    @Override
    public String modelo() {
        return "mock-llm";
    }
}
