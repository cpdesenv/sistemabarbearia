package com.barbearia.ia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "app.ia")
@Getter
@Setter
public class IaProperties {

    /** "mock" (padrao, sem credencial) ou "anthropic" (chama a API de verdade). */
    private String gateway = "mock";

    private String apiKey = "";

    /** claude-sonnet-5 por padrao — custo bem menor que claude-opus-5 para um chat de agendamento, decisao do produto. */
    private String modelo = "claude-sonnet-5";

    /** Preco por milhao de tokens (USD), so para calcular o custo estimado registrado em UsoLlm. */
    private double precoEntradaPorMilhaoUsd = 2.0;
    private double precoSaidaPorMilhaoUsd = 10.0;

    /** Cotacao USD->BRL usada so para converter o custo estimado para centavos (exibicao no painel). */
    private double cotacaoUsdBrl = 5.5;

    /** Limite de idas-e-voltas de tool-calling dentro do processamento de UMA mensagem recebida (nao e o limite de turnos da conversa). */
    private int maxIteracoesFerramentas = 8;

    /** Minutos sem mensagem apos os quais o proximo turno da IA reinicia o contexto (timeout do PRD). */
    private int timeoutContextoMinutos = 30;
}
