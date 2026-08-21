# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/).

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
