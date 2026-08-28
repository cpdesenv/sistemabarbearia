# Agente de IA de atendimento — Fases 10 e 11

Este documento explica como o agente de IA (atendimento, agendamento,
cancelamento e remarcação via WhatsApp) funciona: arquitetura, guardrails,
como testar com o LLM mockado e o que muda quando uma chave de API da
Anthropic de verdade for configurada.

## Visão geral

O módulo `com.barbearia.ia` implementa o agente que substitui o eco da Fase
9. A arquitetura é a mesma exigida pelo PRD: **o LLM nunca decide
disponibilidade, preço, nem grava nada sozinho** — ele só conversa e pede a
execução de *tools*, que são métodos Java comuns chamando os serviços já
existentes (`ServicoService`, `ProfissionalService`, `AvailabilityService`,
`AgendamentoService`, `ClienteRepository`). Toda regra de negócio continua
em Java.

- `AiAgentGateway` — fronteira com o provedor de LLM, com tipos de domínio
  neutros (sem nenhum tipo do SDK da Anthropic vazando para fora).
  - `AiAgentGatewayReal` — loop manual de tool-calling via
    `com.anthropic:anthropic-java` (**não** o Tool Runner beta do SDK: no
    Java, o Tool Runner instancia as tools via Jackson a partir do JSON do
    modelo, sem passar pelo Spring — inviabilizaria injetar os serviços do
    domínio nas tools).
  - `MockAiAgentGateway` — determinístico, padrão em dev/teste. Testes
    programam a sequência exata de respostas via `programar(chaveConversa,
    ...)`; sem roteiro programado, cai numa resposta fixa de boas-vindas
    (não é um simulador "inteligente" — só evita erro ao testar a fiação
    manualmente pelo simulador do painel).
- `AgenteTools` — as 10 tools do PRD (`consultar_servicos`,
  `consultar_profissionais`, `consultar_disponibilidade`,
  `identificar_cliente`, `cadastrar_cliente`, `criar_agendamento`,
  `consultar_agendamentos_do_cliente`, `cancelar_agendamento`,
  `remarcar_agendamento`, `escalar_para_humano`).
- `AgenteAtendimentoService` — o orquestrador: dono do loop de tool-calling,
  guardrails de código, tracking de custo e persistência da resposta final
  (reaproveita o outbox de envio da Fase 9, sem nenhuma mudança lá).
- `resources/prompts/atendimento.md` — o system prompt, versionado em
  arquivo (não em string Java).

## Guardrails: código vs. prompt

| Guardrail | Onde é garantido |
|---|---|
| Kill switch (`configuracao_ia.ativo`) | Código — `AgenteAtendimentoService` nunca chama o gateway; `ConfiguracaoIaService` também escala em massa toda conversa em modo IA para HUMANO no instante em que o switch é desligado. |
| Teto de custo mensal | Código — soma de `uso_llm.custo_centavos` do mês corrente, checada antes de cada chamada ao gateway. |
| Limite de turnos por conversa | Código — contador `conversa.turnos_ia`, incrementado a cada resposta final. |
| Timeout de 30 min sem resposta | Código — `conversa.contexto_expira_em`; ao expirar, o histórico enviado ao LLM é reiniciado e o system prompt ganha uma nota de contexto. |
| Nunca inventar disponibilidade/preço/serviço | **Prompt** — estruturalmente, o texto final do agente só tem como conter dados reais se vieram de uma tool, mas nada impede o modelo de escrever texto livre; a garantia é comportamental, validada pela suíte de diálogos-roteiro. |
| Confirmação explícita antes de `criar_agendamento`, `cancelar_agendamento` ou `remarcar_agendamento` | **Prompt** — mesma limitação: reforçado no system prompt, testado no roteiro, não uma trava de código. |
| Resistência a prompt injection | **Estrutural + prompt** — não existe nenhum canal por onde texto do cliente vire instrução de sistema (a mensagem do cliente é sempre um bloco de conteúdo comum no histórico); o prompt reforça isso explicitamente. |
| Cliente das tools é sempre o dono real da conversa | **Código** — nenhuma tool aceita telefone ou `clienteUuid` como argumento; todas usam `conversa.getCliente()`, resolvido pelo backend a partir do remetente real do webhook (`MensageriaInboundService`), nunca de um dado que o LLM tenha recebido do texto do cliente. `cancelar_agendamento`/`remarcar_agendamento` aplicam a mesma checagem sobre o `agendamentoUuid` recebido (`AgenteTools.buscarAgendamentoDoCliente`), devolvendo "Agendamento não encontrado" tanto pra um uuid inexistente quanto pra um de outro cliente. Evita que uma tentativa de injection (ou um erro de raciocínio do modelo) faça uma tool operar sobre o cliente ou agendamento errado — ver os testes `criarAgendamentoIgnoraClienteUuidDeTerceiroEUsaSempreODonoDaConversa` e `cancelarAgendamentoDeOutroClienteNaoEncontraNada`. |
| Política de cancelamento (antecedência mínima) | **Código** — `cancelar_agendamento` compara o horário do agendamento com `barbearia.antecedencia_minima_cancelamento_minutos` (mesma configuração já usada pelo painel, editável em Configurações). Abaixo do mínimo, a tool não cancela: escala a conversa para humano, sem recusar o pedido. |

## Suíte de diálogos-roteiro

`AgenteAtendimentoServiceIntegrationTest` cobre os 10 cenários da Fase 10
exigidos pelo PRD (cliente indeciso, muda de ideia, horário indisponível,
cliente agressivo, mensagem sem sentido, tentativa de injection, cliente
recorrente, dois serviços juntos, data ambígua, cliente que desiste), mais 1
teste de segurança de agendamento e 2 testes de guardrails de código (kill
switch, teto de custo) da Fase 10; e, da Fase 11, os 5 cenários de
cancelamento/remarcação (cancela e libera o slot, remarca pra horário
realmente livre, pergunta qual agendamento quando há mais de um futuro,
nenhuma alteração sem confirmação, cancelamento fora da política escala pra
humano) mais 1 teste de segurança (não cancela agendamento de outro
cliente). Cada cenário programa a sequência exata de respostas do
`MockAiAgentGateway` — as tools chamadas pelo roteiro executam de verdade
contra o banco de teste (Testcontainers), só a *decisão* de qual tool
chamar é que vem do roteiro.

`ConfiguracaoIaControllerIntegrationTest` cobre o endpoint administrativo
(`GET`/`PUT /api/configuracoes/ia`, restrito a `ADMIN`).

## Como testar manualmente

Com o gateway mock (padrão), use o mesmo simulador de WhatsApp da Fase 9
(`/api/dev/whatsapp/inbound`) — a resposta será a de boas-vindas fixa do
mock, já que nenhum roteiro estará programado. Para testar um fluxo
completo manualmente é preciso ativar o gateway real.

## Ativando o gateway real (Anthropic Claude)

1. Defina as variáveis de ambiente:
   - `IA_GATEWAY=anthropic`
   - `ANTHROPIC_API_KEY=<chave de verdade>`
   - `IA_MODELO=claude-sonnet-5` (padrão — pode trocar por outro modelo
     Claude se quiser testar qualidade vs. custo)
2. Reinicie o backend. `AiAgentGatewayReal` passa a ser o bean ativo
   (`@ConditionalOnProperty(prefix = "app.ia", name = "gateway", havingValue
   = "anthropic")`).
3. Acompanhe o custo acumulado pela tela **Conversas** do painel — cada
   chamada ao modelo grava uma linha em `uso_llm` com tokens de entrada/saída
   e custo estimado em centavos (câmbio e preço por milhão de tokens
   configuráveis em `app.ia.*`, ver `application.yml`).

## Painel

A tela **Conversas** tem filtro por status (IA/Humano), mostra o custo de
LLM acumulado por conversa, e o botão "Assumir conversa" (dentro do detalhe
de uma conversa em modo IA) encerra o atendimento automático daquela
conversa especificamente — equivalente ao que `escalar_para_humano` faz via
tool, mas acionado manualmente pelo atendente.

A tela **Configurações > Agente de IA** (`/configuracoes/ia`, só ADMIN) edita
o `configuracao_ia` singleton pelo mesmo endpoint do
`ConfiguracaoIaController`: o kill switch (`ativo`), o limite de turnos por
conversa e o teto de custo mensal em reais (convertido para centavos ao
salvar). Desligar o kill switch por essa tela escala imediatamente, em
massa, todas as conversas em modo IA para HUMANO — mesmo comportamento
validado por `ConfiguracaoIaControllerIntegrationTest` e pelo teste de
diálogo `killSwitchDesligaAIaImediatamenteParaConversasExistentes`.
