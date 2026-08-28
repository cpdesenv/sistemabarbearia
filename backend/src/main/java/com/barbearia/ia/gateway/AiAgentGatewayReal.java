package com.barbearia.ia.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.ToolUseBlockParam;

import com.barbearia.ia.config.IaProperties;

/**
 * Implementacao real via SDK oficial da Anthropic, loop manual (nao o
 * BetaToolRunner): cada tool e um metodo Spring comum com os beans do
 * dominio injetados (ver {@code AgenteTools}), o que o Tool Runner do SDK
 * Java nao permite — ele instancia as classes de tool via Jackson a partir
 * do JSON do modelo, sem passar pelo Spring. O loop de fato (executar as
 * tools e chamar de novo) e responsabilidade do {@code AgenteAtendimentoService};
 * esta classe so traduz UMA ida-e-volta para o formato do SDK.
 */
@Component
@ConditionalOnProperty(prefix = "app.ia", name = "gateway", havingValue = "anthropic")
public class AiAgentGatewayReal implements AiAgentGateway {

    private static final long MAX_TOKENS = 2048L;

    private final AnthropicClient client;
    private final IaProperties propriedades;

    public AiAgentGatewayReal(IaProperties propriedades) {
        this.propriedades = propriedades;
        this.client = AnthropicOkHttpClient.builder().apiKey(propriedades.getApiKey()).build();
    }

    @Override
    public RespostaAgenteIa processarTurno(String chaveConversa, List<TurnoConversa> historico,
            List<DefinicaoFerramentaIa> ferramentas, String systemPrompt) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(propriedades.getModelo())
                .maxTokens(MAX_TOKENS)
                .systemOfTextBlockParams(List.of(TextBlockParam.builder().text(systemPrompt).build()));

        for (DefinicaoFerramentaIa definicao : ferramentas) {
            builder.addTool(paraTool(definicao));
        }
        for (MessageParam mensagem : paraMessageParams(historico)) {
            builder.addMessage(mensagem);
        }

        Message resposta = client.messages().create(builder.build());

        StringBuilder texto = new StringBuilder();
        List<ChamadaFerramenta> chamadas = new ArrayList<>();
        for (ContentBlock bloco : resposta.content()) {
            bloco.text().ifPresent(t -> texto.append(t.text()));
            bloco.toolUse().ifPresent(tu -> chamadas.add(new ChamadaFerramenta(tu.id(), tu.name(), converterEntrada(tu))));
        }

        return new RespostaAgenteIa(texto.isEmpty() ? null : texto.toString(), chamadas,
                (int) resposta.usage().inputTokens(), (int) resposta.usage().outputTokens());
    }

    @Override
    public String modelo() {
        return propriedades.getModelo();
    }

    private Map<String, Object> converterEntrada(ToolUseBlock toolUse) {
        return toolUse._input().convert(new TypeReference<Map<String, Object>>() {
        });
    }

    private Tool paraTool(DefinicaoFerramentaIa definicao) {
        Tool.InputSchema.Properties.Builder propriedadesBuilder = Tool.InputSchema.Properties.builder();
        definicao.propriedades().forEach((nome, schema) -> propriedadesBuilder.putAdditionalProperty(nome,
                JsonValue.from(schema)));

        Tool.InputSchema schema = Tool.InputSchema.builder()
                .properties(propriedadesBuilder.build())
                .required(definicao.obrigatorias())
                .build();

        return Tool.builder()
                .name(definicao.nome())
                .description(definicao.descricao())
                .inputSchema(schema)
                .build();
    }

    /**
     * Traduz o historico neutro para {@code MessageParam}: um turno de assistente com tool_use vira uma mensagem
     * ASSISTANT com blocos de texto+tool_use; um turno de resultados de ferramenta vira uma mensagem USER com
     * blocos tool_result — exatamente o formato que a API exige para o round-trip de tool-calling.
     */
    private List<MessageParam> paraMessageParams(List<TurnoConversa> historico) {
        List<MessageParam> mensagens = new ArrayList<>();
        for (TurnoConversa turno : historico) {
            switch (turno) {
                case TurnoConversa.MensagemUsuario usuario ->
                    mensagens.add(MessageParam.builder().role(MessageParam.Role.USER).content(usuario.texto())
                            .build());
                case TurnoConversa.MensagemAssistente assistente -> mensagens
                        .add(MessageParam.builder().role(MessageParam.Role.ASSISTANT)
                                .contentOfBlockParams(blocosDoAssistente(assistente))
                                .build());
                case TurnoConversa.ResultadosFerramenta resultados -> mensagens
                        .add(MessageParam.builder().role(MessageParam.Role.USER)
                                .contentOfBlockParams(blocosDeResultado(resultados))
                                .build());
            }
        }
        return mensagens;
    }

    private List<ContentBlockParam> blocosDoAssistente(TurnoConversa.MensagemAssistente assistente) {
        List<ContentBlockParam> blocos = new ArrayList<>();
        if (assistente.texto() != null && !assistente.texto().isBlank()) {
            blocos.add(ContentBlockParam.ofText(TextBlockParam.builder().text(assistente.texto()).build()));
        }
        for (ChamadaFerramenta chamada : assistente.chamadasFerramenta()) {
            ToolUseBlockParam.Input.Builder inputBuilder = ToolUseBlockParam.Input.builder();
            chamada.entrada().forEach((chave, valor) -> inputBuilder.putAdditionalProperty(chave,
                    JsonValue.from(valor)));

            blocos.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                    .id(chamada.id())
                    .name(chamada.nome())
                    .input(inputBuilder.build())
                    .build()));
        }
        return blocos;
    }

    private List<ContentBlockParam> blocosDeResultado(TurnoConversa.ResultadosFerramenta resultados) {
        return resultados.resultados().stream()
                .map(resultado -> ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(resultado.chamadaId())
                        .content(resultado.conteudo())
                        .isError(resultado.erro())
                        .build()))
                .toList();
    }
}
