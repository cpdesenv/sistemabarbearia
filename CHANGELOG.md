# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/).

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
