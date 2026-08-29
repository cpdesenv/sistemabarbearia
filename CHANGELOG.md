# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/).

## [0.12.0] - Remoção do atendimento por IA e do canal de WhatsApp

O cliente (Cortes Cavalinho) decidiu não utilizar o atendimento automatizado
por IA nem o canal de mensageria via WhatsApp. As Fases 9 (canal de
mensageria), 6-META (ativação da WhatsApp Cloud API), 10 (agente de IA) e 11
(cancelamento/remarcação pela IA) — todas já entregues e descritas nas
entradas abaixo — foram removidas do produto. A Fase 14 (Automações de
retenção), inteiramente baseada em envio de mensagens e ainda não
implementada, saiu do roadmap pelo mesmo motivo. As fases pendentes
seguintes foram renumeradas para fechar os buracos (ver seção "Status de
implementação" do PRD).

### Removido

- Pacotes de backend `com.barbearia.ia` e `com.barbearia.mensageria` por
  inteiro: agente de IA (`AgenteAtendimentoService`, `AgenteTools`,
  `AiAgentGateway`/`AiAgentGatewayReal`/`MockAiAgentGateway`,
  `ConfiguracaoIa`, `UsoLlm`), canal de mensageria (`Conversa`, `Mensagem`,
  outbox de envio, webhook `/api/webhook/whatsapp`, simulador
  `/api/dev/whatsapp/**`, `WhatsAppRateLimitingFilter`) e os respectivos
  testes de integração.
- Dependência `com.anthropic:anthropic-java` (`backend/pom.xml`).
- Telas de painel `Conversas`, `Simulador de WhatsApp` e `Configurações >
  Agente de IA` (pastas `frontend/src/app/features/mensageria` e
  `features/configuracoes/ia`), rotas e itens de menu correspondentes.
- Variáveis de ambiente `WHATSAPP_GATEWAY`, `WHATSAPP_WEBHOOK_SECRET`,
  `IA_GATEWAY`, `ANTHROPIC_API_KEY`, `IA_MODELO` (e as demais `IA_*` de
  custo/timeout) do `application.yml`, `docker-compose.yml` e
  `.env.example`.
- `docs/agente-ia.md` e `docs/mensageria.md`.
- Migration `V28__remove_ia_e_whatsapp.sql`: `DROP TABLE` de `uso_llm`,
  `configuracao_ia`, `mensagem_envio_outbox`, `mensagem` e `conversa`
  (schema criado pelas migrations V25–V27, agora órfão).

### Mantido (avaliado e decidido não remover)

- `Cliente.whatsapp`/`Cliente.optInWhatsapp` e o valor `WHATSAPP` nos enums
  `OrigemCadastro`/`OrigemAgendamento`: são metadados de contato/origem já
  desacoplados do módulo removido (não disparam nenhum envio), mantidos
  como estão.
- Rota `/api/integracoes/google-calendar/callback` e o restante da
  integração com Google Calendar (Fase 8), sem nenhuma relação com IA ou
  WhatsApp.

### Verificado

- Suíte de testes de backend: 148 testes, sem falhas.
- Suíte de testes de frontend: 16 testes, sem falhas.
- `docker compose down -v` + subida completa: 28 migrations aplicadas sem
  erro; smoke test manual de login, cadastro de cliente (com detecção de
  duplicidade), serviços, caixa, assinaturas e Google Calendar, todos
  funcionando normalmente.
- `/security-review` sobre o diff: nenhum achado — mudança majoritariamente
  de remoção, sem novo endpoint, lógica de autenticação ou input introduzido.

## [0.11.0] - Fase 11 — Cancelamento e remarcação pela IA

### Adicionado

- 2 tools novas em `AgenteTools`: `cancelar_agendamento(agendamentoUuid,
  motivo)` e `remarcar_agendamento(agendamentoUuid, novoInicio)`,
  reaproveitando `AgendamentoService.cancelar()`/`.alterar()` já
  existentes — validação de conflito/antecedência e sincronização com o
  Google Calendar sem nenhuma mudança nesses métodos.
- Guardrail de posse (mesmo padrão da correção de IDOR da Fase 10):
  `cancelar_agendamento`/`remarcar_agendamento` só operam sobre um
  agendamento que pertence ao cliente da conversa — "Agendamento não
  encontrado" tanto para um uuid inexistente quanto para um de outro
  cliente.
- Política de cancelamento configurável: `cancelar_agendamento` usa
  `Barbearia.antecedenciaMinimaCancelamentoMinutos` (campo já existente,
  editável em Configurações, sem consumidor até agora). Abaixo do mínimo,
  não cancela — escala a conversa para atendimento humano.
- Auditoria com origem WhatsApp e o motivo/texto informado pelo cliente
  (`AGENDAMENTO_CANCELADO_VIA_IA`/`AGENDAMENTO_REMARCADO_VIA_IA`).
- System prompt (`atendimento.md`, v2): roteiro de cancelamento/remarcação
  — perguntar qual agendamento quando há mais de um futuro, resumo de
  confirmação obrigatório antes de cancelar/remarcar.
- 6 novos testes de diálogo-roteiro contra o `MockAiAgentGateway` (5
  cenários do PRD + 1 de segurança).

### Corrigido

- `AgendamentoService.criar()` sempre gravava `origem = PAINEL`, mesmo
  quando era o agente de IA criando via WhatsApp (Fase 10) — o enum
  `OrigemAgendamento.WHATSAPP` existia mas nunca era usado. Agora o método
  aceita a origem explicitamente (`WHATSAPP` no caminho da IA, `PAINEL`
  sem mudança no painel/REST).

## [0.10.0] - Fase 10 — Agente de IA: atendimento e agendamento

### Adicionado

- Agente de IA de atendimento via tool-calling (Anthropic Claude),
  substituindo o eco da Fase 9. `AiAgentGateway`: `AiAgentGatewayReal`
  (loop manual de tool-calling via `com.anthropic:anthropic-java` — não o
  Tool Runner beta, que não permite injetar os beans do domínio nas tools)
  e `MockAiAgentGateway` (determinístico, padrão em dev/teste, roteiro
  programável).
- `AgenteTools`: as 8 tools do PRD (`consultar_servicos`,
  `consultar_profissionais`, `consultar_disponibilidade`,
  `identificar_cliente`, `cadastrar_cliente`, `criar_agendamento`,
  `consultar_agendamentos_do_cliente`, `escalar_para_humano`) — cada uma
  um método Java comum chamando serviços já existentes; o LLM nunca decide
  preço, disponibilidade ou grava nada sozinho.
- `AgenteAtendimentoService`: orquestrador do loop de tool-calling e dos
  guardrails de código — kill switch (`configuracao_ia.ativo`), teto de
  custo mensal, limite de turnos por conversa, timeout de contexto de 30
  min. Tracking de tokens/custo por chamada em `uso_llm`.
- System prompt versionado em `resources/prompts/atendimento.md` (não
  hardcoded em string Java).
- Painel: tela Conversas com filtro por status (IA/Humano), custo de LLM
  por conversa e botão "Assumir conversa"; nova tela **Configurações >
  Agente de IA** (`/configuracoes/ia`, só ADMIN) para o kill switch,
  limite de turnos e teto de custo mensal.
- `docs/agente-ia.md` e suíte de 13 testes de diálogo-roteiro contra o
  `MockAiAgentGateway` (10 cenários do PRD + 1 de segurança + 2 guardrails
  de código), sem nenhuma chave de API real.

### Segurança

- Corrigido um IDOR (broken object-level authorization) encontrado em
  `/security-review` antes do PR: `identificar_cliente`,
  `cadastrar_cliente`, `criar_agendamento` e
  `consultar_agendamentos_do_cliente` aceitavam telefone/`clienteUuid`
  vindos da chamada de tool do LLM (em última instância, do texto livre do
  cliente), sem checar contra o dono real da conversa — permitindo que um
  cliente malicioso se passasse por outro (vazando nome/assinatura/
  agendamentos de terceiros, ou criando agendamento em nome de outra
  pessoa). Corrigido: nenhuma tool aceita mais telefone/`clienteUuid` como
  parâmetro — todas usam sempre `conversa.getCliente()`, resolvido pelo
  backend a partir do remetente real do webhook.

## [0.9.0] - Fase 9 — Canal de mensageria (MockWhatsAppGateway)

### Adicionado

- Canal de WhatsApp inteiro funcionando com o `MockWhatsAppGateway`
  (`@ConditionalOnProperty`, padrão) — sem conta Meta, sem
  `CloudApiWhatsAppGateway`, sem token real em lugar nenhum do código
  (fica para a Fase 6-META, sob pedido explícito). `WhatsAppGateway`
  (`sendMessage`/`sendTemplate`/`sendInteractive`/`sendDocument`) já
  desenhado para a Cloud API caber sem mudança de contrato.
- Outbox transacional para o envio (mesmo padrão da Fase 8): mensagem
  SAÍDA nasce com a linha na mesma transação; `MensagemEnvioOutboxWorker`
  processa com backoff exponencial. Status simulado ENVIADA→ENTREGUE→LIDA
  com delay configurável; falha de envio simulável sob demanda (uso
  único), para testar a retentativa deterministicamente.
- Recebimento sem outbox: idempotência via *unique constraint* em
  `mensagem.wa_message_id`, processado em virtual thread (`@Async`) logo
  após o webhook responder 200.
- Webhook no formato da Cloud API (`GET` verificação, `POST` validado por
  `X-Hub-Signature-256`) e simulador (`/api/dev/whatsapp/inbound`,
  `/api/dev/whatsapp/simular-falha`, `/api/dev/status`) reaproveitando o
  mesmo parser/pipeline. Todo `WhatsAppDevController` é
  `@Profile("!prod")` — o bean nem existe em produção.
- Rate limiting por IP (mesmo padrão do login) no webhook e no endpoint
  de injeção.
- Cliente novo por telefone desconhecido nasce como rascunho
  (`origemCadastro=WHATSAPP`); vinculação automática da conversa por
  telefone E.164.
- Frontend: telas Conversas (lista + chat) e Simulador de WhatsApp (só
  aparece no menu se `/api/dev/status` responder habilitado).
- `docs/mensageria.md` e 18 novos testes (unitários e de integração),
  incluindo um teste de regressão que roda o worker de envio numa thread
  sem transação ambiente, reproduzindo o mesmo bug de self-invocation
  corrigido no `CalendarOutboxWorker` (ver "Corrigido" abaixo).

### Corrigido

- `CalendarOutboxWorker` (Fase 8): `processarPendencias()` chamava
  `this.processarUm(id)` diretamente — uma auto-invocação que não passa
  pelo proxy do Spring, então `@Transactional` era silenciosamente
  ignorado. Só se manifestava em produção (o `@Scheduled` real roda numa
  thread do agendador sem transação ambiente): toda tentativa de
  sincronizar um agendamento com o Google Calendar explodia com
  `LazyInitializationException` ao acessar `agendamento.getCliente()`/
  `getProfissional()`. Corrigido com uma referência `@Lazy` ao próprio
  bean, o padrão recomendado pelo Spring para esse caso. O mesmo bug foi
  encontrado e corrigido preventivamente no `MensagemEnvioOutboxWorker`
  (Fase 9) antes de ir para produção.

## [0.8.0] - Fase 8 — Integração com Google Calendar

### Adicionado

- OAuth2 com o Google Calendar: tela Integrações → Google Calendar
  (`/configuracoes/integracoes/google-calendar`, `ADMIN`), botão
  Conectar → fluxo Authorization Code → refresh token criptografado
  (AES-256-GCM, novo `CriptografiaService` genérico em
  `shared/criptografia`) na tabela singleton
  `integracao_google_calendar`. Renovação do access token delegada ao
  `UserCredentials`/`HttpCredentialsAdapter` oficial do Google — sem
  código de refresh manual.
- Outbox transacional (`agendamento_calendar_outbox`, primeiro uso desse
  padrão no projeto): a intenção de sincronizar é gravada na mesma
  transação que confirma/remarca/cancela o agendamento;
  `CalendarOutboxWorker` (`@Scheduled`, mesmo estilo do
  `AssinaturaRenovacaoScheduler`) processa com backoff exponencial
  (1/5/15/30/60 min, até 8 tentativas antes de `FALHA_PERMANENTE`) — uma
  falha na chamada ao Google nunca bloqueia a operação de agenda.
- Ciclo de vida do evento: confirmar agendamento cria o evento (título
  `Serviço(s) — Nome do Cliente`, descrição com telefone/observação,
  fuso horário da barbearia); remarcar atualiza (só se já sincronizado);
  cancelar remove (ou cancela a pendência, se ainda não sincronizado).
  `googleEventId`/`googleCalendarId` salvos no agendamento.
- Configurável: calendário único (com cor por profissional, paleta fixa
  da API do Google) ou um calendário por profissional
  (`profissional.googleCalendarId`).
- Painel de agendamentos fora de sincronia e botão "Ressincronizar
  agenda" (zera tentativas e força reprocessamento imediato).
- `MockCalendarGateway`/`MockGoogleOAuthGateway`
  (`@ConditionalOnProperty`, primeiro uso desse mecanismo no projeto;
  padrão quando `app.calendar.gateway` não é definido) — zero
  credencial Google necessária em dev e em toda a suíte de testes.
  `GoogleCalendarGateway`/`GoogleOAuthGatewayImpl` (implementação real,
  via SDK oficial do Google) prontos para ativar com
  `CALENDAR_GATEWAY=google` quando houver um projeto Google Cloud
  configurado.
- 20 novos testes (`CriptografiaServiceTest`, `CalendarOutboxWorkerTest`,
  `AgendamentoCalendarSyncIntegrationTest`,
  `IntegracaoGoogleCalendarControllerIntegrationTest`), incluindo teste
  automatizado garantindo que o refresh token nunca aparece em log.

**Lacuna conhecida:** 4 dos 7 critérios de aceite da fase (conectar,
evento aparecer no Google, mover/remover evento real, renovação de
token expirado) só puderam ser validados com o gateway mock — sem
projeto Google Cloud configurado ainda. Ver nota na Fase 8 do
`docs/prd-sistema-barbearia.md`.

## [Unreleased] - Reforma de UX/UI do painel administrativo

Fora da numeração de fases (pedida antes da Fase 7); identidade visual
permanece **provisória** até o CHECKPOINT-VISUAL.

### Alterado

- Consolidação dos tokens de design (cores, espaçamento, breakpoints) e
  alinhamento visual do shell e da tela de login à mesma identidade.
- Confirmação de ações destrutivas unificada (`ConfirmDialogService`) em
  bloqueios, comanda, contas a pagar/receber e ativação/desativação de
  produto, serviço e profissional.
- Componente compartilhado de busca de cliente por nome/telefone
  (autocomplete), substituindo a busca duplicada em Agenda e Contas a
  receber.
- Telas de estoque (filtro e paginação), dashboard (estados "em breve" e
  atalhos) e agenda mobile alinhadas ao mesmo padrão visual das demais
  listagens.
- Tela de profissional reformulada (sem abas, tabelas compactas, seletor
  de cor com paleta ampliada e contraste WCAG AA conferido).

### Corrigido

- Histórico da ficha do cliente (`GET /api/clientes/{uuid}/ficha`) nunca
  retornava os agendamentos, atendimentos (comandas) e notas fiscais
  reais do cliente — `ClienteService.ficha()` sempre devolvia listas
  vazias, mesmo com dados de verdade no banco. Corrigido conectando a
  ficha aos dados reais das Fases 4-6 (issue #36).

## [0.7.0] - Fase 7 — Clube Cavalinho / Assinaturas

### Adicionado

- Módulo `assinatura`: `PlanoAssinatura` (preço mensal, cortes inclusos por
  ciclo, desconto percentual em adicionais, serviços que consomem saldo) e
  `Assinatura` (cliente, plano, status `ATIVA`/`CANCELADA`/`INADIMPLENTE`/
  `SUSPENSA`, saldo de cortes, datas de início/próxima renovação/
  cancelamento). Índice único parcial garante no máximo uma assinatura em
  curso (`ATIVA`/`INADIMPLENTE`) por cliente.
- Consumo de saldo integrado à abertura de comanda: serviço incluso no
  plano do cliente assinante, com saldo disponível, nasce como item
  coberto pela assinatura (valor zerado, sem comissão nesta comanda);
  saldo zerado ou serviço fora do plano é cobrado normalmente. Devolução
  de saldo ao remover o item ou estornar a comanda. Ajuste atômico de
  saldo (`AssinaturaRepository.ajustarSaldo`, mesmo padrão de
  `ProdutoRepository.ajustarEstoque`) — testado sob concorrência real (20
  tentativas simultâneas com saldo 1, exatamente uma consome).
- Job diário de renovação (`AssinaturaRenovacaoScheduler`, `@Scheduled`):
  reabastece o saldo para o valor do plano (sem acumular ciclo não usado)
  e gera a mensalidade do próximo ciclo quando a do ciclo anterior foi
  recebida; caso contrário marca a assinatura `INADIMPLENTE` (com
  auditoria) e tenta de novo na execução seguinte. **Sem gateway de
  pagamento** (fora do escopo do projeto): cada mensalidade é uma
  `ContaReceber` comum, recebida manualmente pela recepção, no mesmo fluxo
  já existente de Contas a Receber.
- `GET /api/assinaturas/relatorio-receita`: diferencia receita de
  mensalidades de assinatura (recebidas no mês) de receita avulsa
  (comandas fechadas no mês). `GET /api/assinaturas/resumo`: contagem por
  status e receita recorrente mensal (soma do preço das assinaturas
  ativas).
- Endpoints `/api/planos-assinatura` (CRUD, `ADMIN`/`GERENTE`) e
  `/api/assinaturas` (assinar — `ADMIN`/`GERENTE`/`RECEPCAO`, cancelar —
  `ADMIN`/`GERENTE`, listar por status).
- Frontend: tela **Clube Cavalinho** (`/clube-cavalinho`) com abas
  Assinantes (resumo, nova assinatura via busca de cliente, cancelamento
  com motivo) e Meus planos (CRUD, seleção dos serviços inclusos).
- 8 novos testes de integração cobrindo consumo/esgotamento de saldo,
  unicidade de assinatura por cliente, renovação com mensalidade paga,
  inadimplência e retry, relatório de receita e concorrência no consumo
  de saldo.

## [0.5.0] - Fase 5 — Comanda, pagamento, caixa, estoque e financeiro

### Adicionado

- **5A — Comanda, caixa e formas de pagamento:**
  - Módulo `financeiro`: `Comanda` e `ComandaItem`, sempre vinculada a um
    `Agendamento`. A comanda é aberta automaticamente ao iniciar o
    atendimento (`POST /api/comandas/abrir-para-agendamento/{agendamentoUuid}`,
    idempotente) já com os serviços do agendamento; fecha-la transiciona o
    agendamento para `FINALIZADO` e lança o valor no caixa do dia.
  - Nesta sub-entrega os itens são somente serviços — produtos entram na
    sub-entrega 5B, que vai estender `comanda_item` via nova migration.
  - Desconto com motivo obrigatório, rateado proporcionalmente entre os
    itens (com ajuste de centavo de arredondamento no último item, para a
    soma sempre fechar exatamente com o valor informado).
  - Comissão por item calculada sobre o valor líquido (após o rateio do
    desconto), usando o percentual do vínculo profissional↔serviço quando
    existir, senão o percentual padrão do profissional — recalculada em
    tempo real a cada mudança de item ou desconto.
  - Comanda `FECHADA` é imutável; correção é feita por **estorno** (motivo
    obrigatório, com auditoria), o que libera o agendamento (já
    `FINALIZADO`) para uma nova comanda ser aberta, preservando o
    histórico das comandas anteriores.
  - Índice único parcial no banco (`WHERE status = 'ABERTA'`) garante, sob
    concorrência, no máximo uma comanda aberta por agendamento por vez.
  - Tela **Caixa do dia**: total geral, total por forma de pagamento e
    total (faturado + comissão) por profissional
    (`GET /api/caixa?data=...`).
  - Frontend: tela de Comanda (itens, desconto, forma de pagamento,
    fechar/estornar) e tela de Caixa do dia; o botão "Iniciar atendimento"
    da Agenda agora abre a comanda e navega direto para ela.
  - 9 testes de integração cobrindo abertura idempotente, rateio de
    desconto e comissão com valores exatos, bloqueio de edição de comanda
    fechada, bloqueio de fechamento sem item/forma de pagamento, estorno
    com auditoria e permissões por perfil (barbeiro fecha mas não
    estorna).
- **5B — Produtos e estoque:**
  - Módulo `produto`: CRUD de catálogo (`Produto` — nome, categoria,
    unidade, preço de venda/custo, estoque mínimo, `/api/produtos`,
    mesmos perfis de gestão do CRUD de serviços) e histórico de
    movimentações (`MovimentoEstoque` — entrada, saída, ajuste, devolução).
  - Saldo do produto (`estoque_atual`) fica em cache na própria linha,
    mantido em sincronia com o histórico por um `UPDATE` atômico
    (`ProdutoRepository.ajustarEstoque`, condicionado a
    `estoque_atual + delta >= 0`) — protege contra duas baixas
    concorrentes deixarem o saldo negativo, sem precisar de lock explícito.
  - Entrada de estoque com custo unitário (`POST
    /api/produtos/{uuid}/entrada-estoque`) e ajuste manual de inventário
    com motivo obrigatório (`POST /api/produtos/{uuid}/ajuste-estoque`),
    ambos `ADMIN`/`GERENTE`; extrato paginado por produto (`GET
    /api/produtos/{uuid}/movimentos`) e alerta de estoque mínimo (`GET
    /api/produtos/alertas-estoque-minimo`).
  - `comanda_item` passa a aceitar itens de **produto**, além de serviço
    (`tipo`, `produto_id`, com `servico_id` agora opcional e uma
    constraint de banco garantindo que exatamente um dos dois esteja
    preenchido). Produto não gera comissão, mas entra no rateio do
    desconto como qualquer outro item.
  - Baixa de estoque acontece **somente ao fechar** a comanda (nunca ao
    adicionar o item) — se o saldo não for suficiente para algum produto,
    a `NegocioException` sobe e a transação inteira dá rollback: a comanda
    continua `ABERTA` e nenhum estoque é alterado. O **estorno** devolve a
    quantidade ao estoque automaticamente.
  - Frontend: catálogo de produtos (`/produtos`) e tela de estoque e
    movimentações (`/produtos/estoque` → lista com destaque para saldo
    abaixo do mínimo → `/produtos/:uuid/estoque` com entrada, ajuste e
    extrato); a tela de Comanda ganhou um segundo seletor para adicionar
    produtos; card "Produtos a repor" no Dashboard.
  - 13 novos testes de integração (CRUD de produto, entrada, ajuste sem
    motivo recusado, alerta de estoque mínimo, e a integração completa
    com comanda: venda com saldo zero bloqueada, baixa exata no
    fechamento com extrato ligado à comanda, devolução no estorno, e item
    de produto sem comissão).
- **5C — Despesas, contas a pagar/receber e fluxo de caixa:**
  - Novas entidades no módulo `financeiro`: `Despesa` (lançamento avulso,
    definitivo, sem edição/exclusão), `ContaPagar` e `ContaReceber`
    (`PENDENTE`/`PAGA` ou `RECEBIDA`/`CANCELADA`, com auditoria em toda
    transição). `ContaReceber` referencia um `Cliente` (débito de
    cliente, ex.: serviço fiado).
  - `GET /api/financeiro/fluxo-caixa`: **caixa em mãos** (soma de todas as
    comandas `FECHADA` menos todas as despesas lançadas, histórico
    completo — não só do dia, diferente do Caixa do dia da 5A) **+
    contas a receber esperadas** (toda conta a receber `PENDENTE`,
    independente do vencimento) **− contas a pagar vencidas** (só as
    `PENDENTE` cujo vencimento já passou — uma conta a pagar futura não
    reduz o fluxo projetado). Usa o fuso horário da barbearia para
    definir "hoje", como o Caixa do dia da 5A.
  - Endpoints: `/api/despesas` (`ADMIN`/`GERENTE` lançam), `/api/contas-
    pagar` (`ADMIN`/`GERENTE` lançam, marcam paga ou cancelam) e
    `/api/contas-receber` (`ADMIN`/`GERENTE`/`RECEPCAO` lançam — é tarefa
    de recepção registrar um débito de cliente —, só `ADMIN`/`GERENTE`
    marcam recebida ou cancelam).
  - Frontend: tela **Contas a pagar/receber** (`/financeiro/contas`, com
    abas de Despesas, Contas a pagar e Contas a receber — busca de
    cliente reaproveitada do formulário de agendamento, vencidas
    destacadas em vermelho) e tela **Fluxo de caixa**
    (`/financeiro/fluxo-caixa`) com os 4 números explicados.
  - 5 novos testes de integração cobrindo o efeito de cada lançamento no
    fluxo de caixa (despesa reduz, conta a receber soma até ser
    recebida, conta a pagar só entra quando vencida, comanda fechada
    soma) e permissões (recepção lança conta a receber mas não confirma
    o recebimento).

Com a 5C, a Fase 5 está completa: comanda, caixa, estoque e a visão
financeira consolidada — tudo o que a barbearia precisa para controlar o
dinheiro real antes de qualquer automação (Fase 6 em diante).

## [0.4.0] - Fase 4 — Agenda e motor de disponibilidade

### Adicionado

- `AvailabilityService`: motor de disponibilidade que calcula os horários
  realmente livres de um profissional (ou de todos que realizam os serviços
  pedidos), considerando grade semanal, bloqueios, agendamentos existentes,
  duração total dos serviços selecionados e as antecedências mínima/máxima
  configuradas na barbearia (`GET /api/agenda/disponibilidade`).
- Novo campo `granularidade_slot_minutos` na configuração da barbearia
  (padrão 15), controlando o intervalo entre os horários sugeridos.
- CRUD de agendamentos (`/api/agendamentos`): criar, alterar (remarcar),
  consultar por período/profissional/cliente/status, e as transições de
  estado `confirmar` → `iniciar` → `finalizar`, além de `nao-compareceu` e
  `cancelar` (soft delete, com motivo obrigatório e auditoria em toda
  alteração).
- **Constraint de exclusão no Postgres** (`EXCLUDE USING gist`, com a
  extensão `btree_gist`) impedindo, a nível de banco, dois agendamentos
  sobrepostos para o mesmo profissional — a validação em Java (que dá
  mensagens de erro legíveis para o caso comum) não é suficiente sozinha sob
  concorrência real; quem garante de verdade é o banco. Testado diretamente
  com duas transações concorrentes inserindo o mesmo horário, sem leitura
  prévia entre elas: exatamente uma é aceita, sempre.
- Tabelas `agendamento` e `agendamento_servico` (com snapshot de preço e
  duração do serviço no momento do agendamento, para não alterar
  retroativamente agendamentos já criados quando um serviço muda de preço).
- Frontend: tela **Agenda** com visão dia (colunas por profissional, clique
  numa célula vazia para criar um agendamento, arrastar um agendamento para
  remarcar) e visão semana (lista compacta por dia); formulário de
  agendamento com busca de cliente, seleção de serviços/profissional/
  horário e os botões de transição de status.
- 13 testes de integração cobrindo disponibilidade por duração de serviço,
  remoção de slots por bloqueio, fuso horário, validações (fora do horário
  de funcionamento, no passado, sobreposição), fluxo completo de status,
  remarcação, permissões e a constraint de exclusão sob concorrência real.

## [0.3.0] - Fase 3 — Clientes e histórico

### Adicionado

- CRUD de clientes (`GET/POST/PUT /api/clientes`), com busca por nome,
  telefone ou CPF e paginação.
- Normalização automática de telefone para E.164 (`TelefoneNormalizador`,
  reaproveitável pelas fases de mensageria) e validação de CPF pelo dígito
  verificador (`CpfValidador`), ambos em `shared/validacao`.
- Detecção de duplicidade por telefone: cadastro com telefone já existente
  retorna `409 CLIENTE_DUPLICADO` com os dados do cliente já cadastrado, em
  vez de criar um duplicado — telefone tem constraint única no banco (é a
  chave natural que a mensageria usará para identificar o cliente).
- Ficha do cliente (`GET /api/clientes/{uuid}/ficha`): dados cadastrais mais
  o histórico de agendamentos, atendimentos e notas fiscais — listas vazias
  nesta fase (`// TODO(fase-4/5/6)`), já com a estrutura pronta para quando
  essas entidades existirem.
- LGPD: campo de consentimento (com data de registro), exportação de dados
  pessoais (`GET /api/clientes/{uuid}/exportar-dados`) e anonimização lógica
  (`POST /api/clientes/{uuid}/anonimizar`, com motivo obrigatório) — a linha
  é preservada para integridade referencial futura, mas os campos pessoais
  são zerados.
- Frontend: listagem com busca e paginação, formulário de cadastro/edição
  (com aviso e atalho para o cadastro existente em caso de duplicidade) e
  tela de ficha do cliente (dados, histórico e ações de LGPD).
- Permissões: leitura liberada a qualquer perfil autenticado; criar/editar
  cliente para `ADMIN`, `GERENTE` e `RECEPCAO`; exportação e anonimização
  (LGPD) restritas a `ADMIN`/`GERENTE`.
- Seed de exemplo (perfil dev): 3 clientes com diferentes origens de
  cadastro e status de consentimento.

## [0.2.0] - Fase 1 — Segurança, usuários e auditoria

### Adicionado

- Entidades `Usuario`/`Perfil` (`ADMIN`, `GERENTE`, `BARBEIRO`, `RECEPCAO`) e
  tabela `auditoria` genérica, reaproveitada pelas próximas fases.
- Autenticação por JWT: `POST /api/auth/login`, `/api/auth/refresh` (com
  rotação e revogação do refresh token) e `/api/auth/logout`.
- Spring Security com filtro JWT, autorização por perfil (`@PreAuthorize`) e
  respostas de erro em JSON padronizado para 401/403.
- Rate limiting (Bucket4j, em memória) no endpoint de login.
- Tratamento global de exceções (`@RestControllerAdvice`) com formato de erro
  padronizado em português.
- Usuário administrador inicial criado por migration Java do Flyway, a partir
  de `ADMIN_EMAIL`/`ADMIN_PASSWORD`.
- Frontend: tela de login (Angular Material), `AuthGuard`, `RoleGuard`,
  interceptor HTTP com renovação automática de token e logout; menu lateral
  passa a ser filtrado por perfil do usuário logado.
- Angular Material adicionado ao painel (tema Material 3 provisório) e proxy
  `/api` configurado no Nginx (prod) e no `ng serve` (dev), evitando CORS.

## [0.1.0] - Fase 0 — Fundação e ambiente

### Adicionado

- Estrutura do monorepo (`backend/`, `frontend/`, `infra/`, `docs/`).
- Backend Spring Boot 3.5.16 (Java 21) com Actuator, perfis `dev`/`test`/`prod`,
  Flyway com migration inicial (habilita `pgcrypto`) e Swagger/OpenAPI.
- Endpoint `GET /api/health` retornando `{"status":"UP"}`.
- Frontend Angular 22 com layout base (sidebar + topbar) e rotas `/login` e
  `/dashboard` (placeholder).
- `docker-compose.yml` (dev) com Postgres, backend, frontend e MinIO;
  `docker-compose.prod.yml` inicial.
- Dockerfiles multi-stage para backend (JRE 21 slim, usuário não-root) e
  frontend (build Node → Nginx).
- Pipeline de CI (GitHub Actions) rodando build e testes de backend e
  frontend a cada push.
- `.env.example`, `README.md`, `docs/limitacoes.md`.
