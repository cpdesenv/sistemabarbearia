package com.barbearia.ia.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.ia.config.IaProperties;
import com.barbearia.ia.domain.ConfiguracaoIa;
import com.barbearia.ia.domain.UsoLlm;
import com.barbearia.ia.gateway.AiAgentGateway;
import com.barbearia.ia.gateway.ChamadaFerramenta;
import com.barbearia.ia.gateway.RespostaAgenteIa;
import com.barbearia.ia.gateway.ResultadoFerramenta;
import com.barbearia.ia.gateway.TurnoConversa;
import com.barbearia.ia.repository.ConfiguracaoIaRepository;
import com.barbearia.ia.repository.UsoLlmRepository;
import com.barbearia.ia.tools.AgenteTools;
import com.barbearia.mensageria.domain.Conversa;
import com.barbearia.mensageria.domain.DirecaoMensagem;
import com.barbearia.mensageria.domain.Mensagem;
import com.barbearia.mensageria.domain.ModoAtendimento;
import com.barbearia.mensageria.repository.ConversaRepository;
import com.barbearia.mensageria.repository.MensagemRepository;
import com.barbearia.mensageria.service.MensageriaEnvioService;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * O orquestrador do agente de IA de atendimento (PRD, Fase 10): dono do
 * loop de tool-calling. A cada mensagem recebida (chamado por
 * {@code MensageriaInboundService}), monta o historico da conversa, chama o
 * {@link AiAgentGateway} e executa as tools pedidas (via {@link AgenteTools})
 * ate o LLM devolver um texto final — que vira uma mensagem SAIDA normal,
 * reaproveitando o outbox da Fase 9.
 *
 * <p>Toda regra de negocio (disponibilidade, preco, criacao do agendamento)
 * continua em Java, dentro das tools — o LLM so decide QUANDO chamar cada
 * uma. Os guardrails de kill switch, teto de custo e limite de turnos sao
 * aplicados aqui, em codigo; os demais guardrails do PRD (confirmacao
 * explicita, resistencia a prompt injection, tom) sao responsabilidade do
 * system prompt ({@code resources/prompts/atendimento.md}) e validados pela
 * suite de dialogos-roteiro contra o LLM mockado.
 */
@Service
@RequiredArgsConstructor
public class AgenteAtendimentoService {

    private static final Logger log = LoggerFactory.getLogger(AgenteAtendimentoService.class);
    private static final String CAMINHO_PROMPT = "prompts/atendimento.md";

    private final ConversaRepository conversaRepository;
    private final MensagemRepository mensagemRepository;
    private final MensageriaEnvioService envioService;
    private final ConfiguracaoIaRepository configuracaoIaRepository;
    private final UsoLlmRepository usoLlmRepository;
    private final AiAgentGateway aiAgentGateway;
    private final AgenteTools agenteTools;
    private final IaProperties iaProperties;

    private final String systemPrompt = carregarSystemPrompt();

    @Transactional
    public void responder(Conversa conversa) {
        ConfiguracaoIa configuracao = obterConfiguracao();

        if (!configuracao.isAtivo()) {
            escalar(conversa, "Kill switch da IA esta desligado.");
            return;
        }
        if (conversa.getModoAtendimento() == ModoAtendimento.HUMANO) {
            return;
        }
        if (custoDoMesAtingiuOTeto(configuracao)) {
            escalar(conversa, "Teto de custo mensal da IA foi atingido.");
            return;
        }

        boolean contextoReiniciado = contextoExpirou(conversa);
        List<TurnoConversa> historico = montarHistorico(conversa, contextoReiniciado);
        String promptEfetivo = contextoReiniciado ? systemPrompt + PROMPT_CONTEXTO_REINICIADO : systemPrompt;
        String chaveConversa = conversa.getTelefoneE164();

        int iteracoes = 0;
        while (true) {
            if (iteracoes++ >= iaProperties.getMaxIteracoesFerramentas()) {
                log.warn("Limite de iteracoes de tool-calling atingido na conversa {}", conversa.getUuidPublico());
                escalar(conversa, "Limite de chamadas de ferramenta em um unico turno foi atingido.");
                return;
            }

            RespostaAgenteIa resposta = aiAgentGateway.processarTurno(chaveConversa, historico,
                    agenteTools.definicoes(), promptEfetivo);
            registrarUso(conversa, resposta);

            if (!resposta.requerExecucaoDeFerramentas()) {
                if (resposta.textoFinal() != null && !resposta.textoFinal().isBlank()) {
                    envioService.enfileirarEnvio(conversa, resposta.textoFinal());
                }
                incrementarTurno(conversa, configuracao, contextoReiniciado);
                return;
            }

            historico = new ArrayList<>(historico);
            historico.add(new TurnoConversa.MensagemAssistente(resposta.textoFinal(), resposta.chamadasFerramenta()));

            List<ResultadoFerramenta> resultados = new ArrayList<>();
            for (ChamadaFerramenta chamada : resposta.chamadasFerramenta()) {
                resultados.add(agenteTools.executar(chamada, conversa));
            }
            historico.add(new TurnoConversa.ResultadosFerramenta(resultados));

            if (conversa.getModoAtendimento() == ModoAtendimento.HUMANO) {
                // uma tool (escalar_para_humano) mudou o modo no meio do loop — para aqui, sem mandar mais nada como IA.
                return;
            }
        }
    }

    private static final String PROMPT_CONTEXTO_REINICIADO = "\n\n## Contexto desta chamada\nO cliente ficou mais "
            + "de 30 minutos sem responder. Cumprimente novamente, de forma breve, antes de continuar.";

    private boolean contextoExpirou(Conversa conversa) {
        return conversa.getContextoExpiraEm() != null && Instant.now().isAfter(conversa.getContextoExpiraEm());
    }

    /** Se o contexto expirou, descarta o historico anterior — so a mensagem recem-recebida entra no prompt. */
    private List<TurnoConversa> montarHistorico(Conversa conversa, boolean contextoReiniciado) {
        List<Mensagem> mensagens = mensagemRepository.findByConversaOrderByCriadoEmAsc(conversa);
        if (contextoReiniciado && !mensagens.isEmpty()) {
            mensagens = List.of(mensagens.get(mensagens.size() - 1));
        }
        List<TurnoConversa> historico = new ArrayList<>();
        for (Mensagem mensagem : mensagens) {
            if (mensagem.getConteudo() == null) {
                continue;
            }
            historico.add(mensagem.getDirecao() == DirecaoMensagem.ENTRADA
                    ? new TurnoConversa.MensagemUsuario(mensagem.getConteudo())
                    : new TurnoConversa.MensagemAssistente(mensagem.getConteudo(), List.of()));
        }
        return historico;
    }

    private void incrementarTurno(Conversa conversa, ConfiguracaoIa configuracao, boolean contextoReiniciado) {
        conversa.setTurnosIa(contextoReiniciado ? 1 : conversa.getTurnosIa() + 1);
        conversa.setContextoExpiraEm(Instant.now().plus(iaProperties.getTimeoutContextoMinutos(), ChronoUnit.MINUTES));
        if (conversa.getTurnosIa() >= configuracao.getLimiteTurnos()) {
            conversa.setModoAtendimento(ModoAtendimento.HUMANO);
            conversa.setMotivoEscalonamento("Limite de turnos da conversa atingido.");
        }
        conversaRepository.save(conversa);
    }

    private void escalar(Conversa conversa, String motivo) {
        conversa.setModoAtendimento(ModoAtendimento.HUMANO);
        conversa.setMotivoEscalonamento(motivo);
        conversaRepository.save(conversa);
    }

    private void registrarUso(Conversa conversa, RespostaAgenteIa resposta) {
        BigDecimal custoUsd = BigDecimal.valueOf(resposta.tokensEntrada())
                .multiply(BigDecimal.valueOf(iaProperties.getPrecoEntradaPorMilhaoUsd()))
                .add(BigDecimal.valueOf(resposta.tokensSaida())
                        .multiply(BigDecimal.valueOf(iaProperties.getPrecoSaidaPorMilhaoUsd())))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
        BigDecimal custoCentavos = custoUsd.multiply(BigDecimal.valueOf(iaProperties.getCotacaoUsdBrl()))
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);

        UsoLlm uso = new UsoLlm();
        uso.setConversa(conversa);
        uso.setModelo(aiAgentGateway.modelo());
        uso.setTokensEntrada(resposta.tokensEntrada());
        uso.setTokensSaida(resposta.tokensSaida());
        uso.setCustoCentavos(custoCentavos);
        usoLlmRepository.save(uso);
    }

    private boolean custoDoMesAtingiuOTeto(ConfiguracaoIa configuracao) {
        Instant inicioDoMes = LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.firstDayOfMonth())
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal custoDoMes = usoLlmRepository.somarCustoCentavosDesde(inicioDoMes);
        return custoDoMes.compareTo(BigDecimal.valueOf(configuracao.getTetoCustoMensalCentavos())) >= 0;
    }

    private ConfiguracaoIa obterConfiguracao() {
        return configuracaoIaRepository.findById(ConfiguracaoIa.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao do agente de IA nao encontrada. Verifique se as migrations foram executadas."));
    }

    private static String carregarSystemPrompt() {
        try (InputStream is = AgenteAtendimentoService.class.getClassLoader().getResourceAsStream(CAMINHO_PROMPT)) {
            if (is == null) {
                throw new IllegalStateException("Prompt nao encontrado no classpath: " + CAMINHO_PROMPT);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar o system prompt do agente de IA.", e);
        }
    }
}
