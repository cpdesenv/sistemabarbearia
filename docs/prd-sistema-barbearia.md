# PRD — Sistema para Barbearia

> Documento de Requisitos do Produto. É a fonte da verdade de escopo, fases,
> entregáveis e critérios de aceite do projeto. O `CLAUDE.md` na raiz do
> repositório traz apenas as instruções de comportamento de trabalho e
> aponta para este arquivo.
>
> **Nota de origem:** este PRD consolida o prompt de especificação original
> (histórico de 4 versões, a última com as fases reordenadas por prioridade
> de negócio) com o que já foi de fato construído. Onde o prompt original
> continha apenas uma referência a "conteúdo idêntico à versão anterior"
> sem o texto em si (Stack, Arquitetura, Modelo de Domínio, Fases 0–4 e
> Fase 6-META), o conteúdo abaixo foi reconstruído a partir do
> [`README.md`](../README.md), do [`CHANGELOG.md`](../CHANGELOG.md) e do
> histórico de commits — ou seja, a partir do que já foi decidido e
> implementado, não de um texto original perdido. Cada seção reconstruída
> está sinalizada como tal.

---

## 1. Objetivo do sistema

Automatizar o atendimento e o agendamento de uma barbearia de pequeno/médio
porte (**Cortes Cavalinho**), hoje feito manualmente por WhatsApp, com
ênfase em **controle financeiro confiável** desde o início.

**Fluxo central:**

1. O dono e sua equipe registram o que acontece de verdade (agenda,
   clientes, atendimento, caixa) — dados confiáveis são o pré-requisito.
2. A dinâmica financeira fica visível: distinguir faturamento de caixa,
   receita recorrente (Clube Cavalinho) de serviços avulsos, e compromissos
   futuros.
3. Depois, automações (IA, lembretes, recuperação de clientes) entram para
   **reduzir fricção**, não para definir as regras.
4. O cliente entra em contato por um dos canais: mensageria (WhatsApp,
   simulado até a conta Meta ser aprovada) ou portal público de
   autoagendamento.
5. Um agente de IA conduz a conversa, mas usa *tools* de Java para tudo que
   importa (disponibilidade, preço, agendamento) — o LLM nunca decide
   regra de negócio sozinho.
6. Registro completo: cliente, agendamento, serviços, profissional,
   histórico.
7. Dashboard e relatórios que respondem "estou melhorando?" em 10 segundos.

## 2. Escopo estrutural — decisão fechada

O sistema atende **uma única barbearia**. Não é multi-tenant e não deve ser
preparado para se tornar multi-tenant:

- Não existe coluna `barbearia_id` como discriminador de tenant em tabelas
  de negócio.
- Não existe resolução de tenant por subdomínio, header, claim de JWT ou
  `@Filter` do Hibernate.
- Não existe Row Level Security para isolamento.
- A tabela `barbearia` é um singleton de configuração (nome, CNPJ,
  endereço, fuso, políticas) — uma linha, criada por migration (`V6__cria_
  tabela_barbearia.sql`, reforçada por `CHECK (id = 1)`), editável no
  painel, nunca listada nem criada pela API.

Se em algum momento fizer sentido generalizar para várias barbearias,
**não faça** — registre a observação em
[`docs/limitacoes.md`](limitacoes.md) e siga o escopo. Simplicidade agora
vale mais do que flexibilidade que talvez nunca seja usada. Ver
`docs/limitacoes.md` para o registro completo de decisões de escopo já
fechadas (mensageria em mock, fim de vida do Spring Boot 3.5.x, tema visual
provisório).

## 3. Stack tecnológica

*Reconstruído a partir do `README.md`, já refletindo o que está em uso.*

| Camada | Tecnologia | Versão |
|---|---|---|
| Backend | Java | 21 LTS |
| Backend | Spring Boot | 3.5.16 |
| Frontend | Angular | 22.1.x |
| Banco de dados | PostgreSQL | 17.11 |
| Storage de objetos | MinIO (S3-compatível) | latest |
| Segurança | Spring Security + JWT (access token 15 min, refresh token opaco de 7 dias, rotacionado a cada uso) | — |
| Rate limiting | Bucket4j, em memória | — |
| Migrations | Flyway | — |

**Integrações externas** — sempre atrás de uma interface (`*Gateway`), com
implementação mock por padrão em dev/test e implementação real plugada só
quando a credencial existir; a troca é sempre por configuração
(`application.yml`/variável de ambiente), nunca por alteração de código de
domínio:

| Integração | Mock (padrão em dev/test) | Real | Fase |
|---|---|---|---|
| WhatsApp | `MockWhatsAppGateway` | `CloudApiWhatsAppGateway` (adiada) | 9 / 6-META |
| Google Calendar | `CalendarGateway` mock | OAuth2 + Calendar API v3 | 8 |
| IA | `AiAgentGateway` mock determinístico | Anthropic Claude / OpenAI | 10 |
| Fiscal | Recibo em PDF (`ReciboFiscalGateway`) | Provedor de NFS-e | 6 / 16 |

## 4. Arquitetura

*Reconstruído a partir do `README.md` §§ "Arquitetura" e "Decisões
arquiteturais".*

Monorepo com backend, frontend e infraestrutura desacoplados, containerizado
desde a Fase 0:

```
Sistema Barbearia/
├── backend/    Spring Boot 3.5.16 (Java 21) — API REST
├── frontend/   Angular 22 — painel administrativo + (Fase 19) portal público
├── infra/      docker-compose (dev e prod)
└── docs/       documentação complementar
```

Decisões arquiteturais vigentes:

- **PostgreSQL**: manutenção simples (`pg_dump`/`pg_restore`), extensões
  maduras (`btree_gist` para a constraint de anti-sobreposição de
  horários), paridade total entre local e produção.
- **BIGINT interno + UUID público**: melhor performance de índice/join
  internamente, sem expor identificadores sequenciais em URLs.
- **Package-by-feature**: cada módulo de negócio (`agenda`, `cliente`,
  `financeiro`...) concentra controller/service/repository/domain/dto —
  reduz o custo de navegação e acoplamento entre camadas transversais.
- **Padrão outbox** para toda chamada externa que pode falhar (Calendar,
  WhatsApp, fiscal): a operação principal nunca é derrubada por uma
  integração fora do ar.
- **Tool/function calling para o agente de IA** (a partir da Fase 10): o
  LLM nunca decide disponibilidade, preço ou grava dado sozinho — toda
  regra de negócio permanece em Java.
- **Refresh token opaco e revogável** (não outro JWT): fica em tabela
  própria com hash SHA-256, é rotacionado a cada uso e pode ser revogado de
  verdade no logout.
- **Rate limiting em memória** (Bucket4j) no login, sem depender de Redis —
  que só entra no projeto a partir da Fase 10 (fila/cache do agente de IA).
- **Proxy `/api`** no Nginx (prod) e no `ng serve` (dev): o frontend sempre
  fala com o backend pela mesma origem, então não há necessidade de liberar
  CORS.
- Auditoria genérica (tabela `auditoria`) reaproveitada por todas as fases
  que alteram dado sensível ou financeiro.
- Horários armazenados em UTC; conversão para o fuso da barbearia
  (`fuso_horario` da tabela `barbearia`, padrão `America/Sao_Paulo`) feita
  na borda (API/frontend).

Regra de arquitetura para toda integração externa (Google Calendar,
WhatsApp, IA, fiscal): interface própria, nenhuma regra de negócio dentro
do adapter, padrão outbox para não travar o fluxo principal em caso de
falha, e testes/CI que nunca dependem de credencial real.

## 5. Status de implementação

*Derivado do `CHANGELOG.md` e do histórico de commits — leitura em
2026-08-27.*

| Fase | Descrição | Status |
|---|---|---|
| 0 | Fundação e ambiente | ✅ Concluída |
| 1 | Segurança, usuários e auditoria | ✅ Concluída |
| 2 | Cadastros base (barbearia, serviços, profissionais, horários/bloqueios) | ✅ Concluída |
| 3 | Clientes e histórico | ✅ Concluída |
| 4 | Agenda e motor de disponibilidade | ✅ Concluída |
| 5 | Comanda, pagamento, caixa, estoque e financeiro (5A/5B/5C) | ✅ Concluída |
| 6 | Comprovante de serviço (PDF) | ✅ Concluída |
| 7 | Clube Cavalinho / Assinaturas | ✅ Concluída |
| 8 | Integração com Google Calendar | ✅ Concluída (mock — ativação real pendente de credencial Google) |
| 9 | Canal de mensageria (MockWhatsAppGateway) | ⬜ Pendente |
| 6-META | Ativação da WhatsApp Cloud API | ⬜ Adiada (só sob pedido explícito) |
| 10 | Agente de IA: atendimento e agendamento | ⬜ Pendente |
| 11 | Cancelamento e remarcação pela IA | ⬜ Pendente |
| 12 | Link de autoagendamento | ⬜ Pendente |
| 13 | Dashboard | ⬜ Pendente |
| 14 | Automações de retenção | ⬜ Pendente |
| 15 | Relatórios comparativos | ⬜ Pendente |
| 16 | NFS-e real | ⬜ Pendente |
| 17 | Gestão de estoque avançada | ⬜ Pendente |
| 18 | Produção, observabilidade e LGPD | ⬜ Pendente |
| CHECKPOINT-VISUAL | Validação de identidade visual com o cliente | ⬜ Pendente (antes da Fase 19) |
| 19 | Portal público de autoagendamento | ⬜ Pendente |

Também já entregue, fora da numeração de fases: um primeiro rascunho de
identidade visual do painel administrativo (paleta extraída da logo CP
Desenv, layout de tabelas, responsividade mobile), aplicado durante a Fase
5 — **provisório**, sujeito à validação formal no CHECKPOINT-VISUAL (ver
Fase 5 e CHECKPOINT-VISUAL abaixo).

Também fora da numeração de fases, pedida antes da Fase 7: uma reforma de
UX/UI do painel administrativo que consolidou os tokens de design (cores,
espaçamento, breakpoints) e alinhou shell/login à mesma identidade visual,
unificou a confirmação de ações destrutivas e a busca de cliente (agora um
componente compartilhado), e aplicou o mesmo padrão visual às telas
restantes (estoque, dashboard, agenda mobile, profissional) — igualmente
**provisória**, sujeita à mesma validação no CHECKPOINT-VISUAL.

## 6. Ritual de trabalho por fase

Cada fase segue o mesmo ritual (regra operacional — a versão que rege o
comportamento da Claude vive no `CLAUDE.md`; aqui fica registrada por
completude):

- Uma fase por vez; nunca implementar fases futuras antecipadamente.
- Explicar as decisões técnicas em português do Brasil antes/durante a
  implementação.
- Ao concluir a implementação (código + testes + migrations Flyway +
  documentação de como executar/testar), verificar um a um os critérios de
  aceite da fase — só marcar `[x]` o que foi realmente confirmado (por
  teste automatizado ou verificação manual), nunca por suposição.
  Apresentar o checklist preenchido e **parar para validação humana** antes
  de qualquer ação abaixo.
- A partir da Fase 6 (inclusive): rodar `/security-review` sobre o diff da
  branch antes de abrir o Pull Request. Achados de severidade relevante
  (segredos, injeção, autorização quebrada, dado sensível exposto em log
  etc.) são corrigidos antes do PR; achados de baixo risco ou que exigem
  decisão de produto são levados para a validação humana junto com o
  restante da fase, nunca ficam silenciosamente ignorados.
- Só após aprovação explícita do checklist de critérios de aceite: criar o
  commit da fase (mensagem sugerida no PRD), publicar a branch e abrir o
  Pull Request.
- Acompanhar a pipeline de CI do PR e reportar o resultado. Se algum check
  falhar, corrigir e reenviar — nunca ignorar ou pular verificação de CI.
- Com a CI verde, pedir uma segunda aprovação explícita, específica para o
  merge (não reaproveitar a aprovação do commit/PR), antes de mesclar a
  branch em `master`.
- Após o merge, atualizar a seção "Status de implementação" deste documento
  e o `CHANGELOG.md` marcando a fase como concluída.

## 7. Fases de implementação

Fases marcadas com **[sub-entregas]** são apresentadas em blocos menores
dentro da mesma fase, para validação em partes. A Fase 6-META só é
executada quando a conta Meta for aprovada; o CHECKPOINT-VISUAL só é
executado quando o cliente validar a identidade visual, antes da Fase 19.

### FASE 0 — Fundação e ambiente ✅

*Reconstruído a partir do `CHANGELOG.md` [0.1.0] — conteúdo original do
prompt (placeholder "idêntico à V3") não disponível no repositório.*

**Entregue:**

- Estrutura de monorepo (`backend/`, `frontend/`, `infra/`, `docs/`).
- Backend Spring Boot 3.5.16 (Java 21) com Actuator, perfis `dev`/`test`/
  `prod`, Flyway com migration inicial (`pgcrypto`) e Swagger/OpenAPI.
- Endpoint `GET /api/health` retornando `{"status":"UP"}`.
- Frontend Angular 22 com layout base (sidebar + topbar) e rotas `/login` e
  `/dashboard` (placeholder).
- `docker-compose.yml` (dev) com Postgres, backend, frontend e MinIO;
  `docker-compose.prod.yml` inicial.
- Dockerfiles multi-stage (backend: JRE 21 slim, usuário não-root;
  frontend: build Node → Nginx).
- Pipeline de CI (GitHub Actions) rodando build e testes a cada push.
- `.env.example`, `README.md`, `docs/limitacoes.md`.

`git commit` de referência: `0.1.0`.

### FASE 1 — Segurança, usuários e auditoria ✅

*Reconstruído a partir do `CHANGELOG.md` [0.2.0] e do `README.md` §
"Autenticação".*

**Entregue:**

- Entidades `Usuario`/`Perfil` (`ADMIN`, `GERENTE`, `BARBEIRO`, `RECEPCAO`)
  e tabela `auditoria` genérica, reaproveitada pelas fases seguintes.
- Autenticação por JWT: `POST /api/auth/login`, `/api/auth/refresh` (com
  rotação e revogação do refresh token) e `/api/auth/logout`.
- Spring Security com filtro JWT, autorização por perfil (`@PreAuthorize`)
  e respostas de erro em JSON padronizado (`{timestamp, status, erro,
  mensagem, caminho, campos}`) para 401/403.
- Rate limiting no login (Bucket4j, 5 tentativas/minuto por IP,
  configurável); ao estourar, `429` com `"erro":"LIMITE_DE_TENTATIVAS_
  EXCEDIDO"`.
- Tratamento global de exceções (`@RestControllerAdvice`).
- Usuário administrador inicial criado por migration a partir de
  `ADMIN_EMAIL`/`ADMIN_PASSWORD` (se omitidas, nenhum admin é criado
  automaticamente — assim a suíte de testes roda sem credencial).
- Frontend: tela de login (Angular Material), `AuthGuard`, `RoleGuard`,
  interceptor HTTP com renovação automática de token e logout; menu
  lateral filtrado por perfil.
- Angular Material com tema Material 3 provisório; proxy `/api` no Nginx
  (prod) e no `ng serve` (dev), evitando CORS.

`git commit` de referência: `0.2.0`.

### FASE 2 — Cadastros base [sub-entregas] ✅

*Reconstruído a partir do histórico de commits (`Fase 2A` a `Fase 2D`) —
sem entrada dedicada no `CHANGELOG.md`; conteúdo original do prompt não
disponível no repositório.*

**Sub-entregas entregues:**

- **2A** — Configuração da barbearia: leitura/edição do registro singleton
  (nome, CNPJ, contato, endereço, fuso horário, antecedências mínima/
  máxima de agendamento e de cancelamento).
- **2B** — CRUD de serviços (nome, descrição, categoria, preço, duração).
- **2C** — CRUD de profissionais, com vínculo a serviços (percentual de
  comissão específico por vínculo, com fallback para o percentual padrão
  do profissional) e cor de identificação na agenda.
- **2D** — Grade de horários de funcionamento e bloqueios (folgas, exceções
  pontuais) por profissional.
- Seed de dados de exemplo para o perfil `dev` (barbearia Cortes Cavalinho,
  3 profissionais, catálogo de serviços).

### FASE 3 — Clientes e histórico ✅

*Reconstruído a partir do `CHANGELOG.md` [0.3.0] e do `README.md` §
"Clientes".*

**Entregue:**

- CRUD de clientes (`GET/POST/PUT /api/clientes`), busca por nome,
  telefone ou CPF, com paginação.
- Normalização automática de telefone para E.164 e validação de CPF pelo
  dígito verificador.
- Detecção de duplicidade por telefone: cadastro com telefone já existente
  retorna `409 CLIENTE_DUPLICADO` com os dados do cliente já cadastrado —
  telefone é único no banco (chave que a mensageria usa para identificar o
  cliente).
- Ficha do cliente (`GET /api/clientes/{uuid}/ficha`): dados + histórico de
  agendamentos/atendimentos/notas fiscais.
- LGPD: consentimento (com data de registro), exportação de dados pessoais
  (`GET /api/clientes/{uuid}/exportar-dados`) e anonimização lógica (`POST
  /api/clientes/{uuid}/anonimizar`, motivo obrigatório — a linha é
  preservada para integridade referencial, campos pessoais zerados).
- Permissões: leitura para qualquer perfil autenticado; criar/editar para
  `ADMIN`/`GERENTE`/`RECEPCAO`; exportação e anonimização restritas a
  `ADMIN`/`GERENTE`.
- Frontend: listagem com busca/paginação, formulário de cadastro/edição
  (com atalho para o cadastro existente em duplicidade) e tela de ficha.

`git commit` de referência: `0.3.0`.

**Bug corrigido (registrado em 2026-08-26, corrigido em 2026-08-26):** o
histórico da ficha (agendamentos/atendimentos/notas fiscais) não estava
conectado aos dados reais das Fases 4-6 — `ClienteService.ficha()`
retornava sempre listas vazias. Corrigido conectando a ficha aos dados
reais de agendamento, comanda e comprovante do cliente. Rastreado em
[issue #36](https://github.com/cpdesenv/sistemabarbearia/issues/36).

### FASE 4 — Agenda e motor de disponibilidade ✅

*Reconstruído a partir do `CHANGELOG.md` [0.4.0] e do `README.md` §
"Agenda e motor de disponibilidade".*

**Entregue:**

- `AvailabilityService`: motor de disponibilidade que calcula horários
  realmente livres de um profissional (ou de todos que realizam os
  serviços pedidos), considerando grade semanal, bloqueios, agendamentos
  existentes, duração total dos serviços e antecedências mínima/máxima
  configuradas na barbearia (`GET /api/agenda/disponibilidade`).
- CRUD de agendamentos (`/api/agendamentos`): criar, remarcar, consultar
  por período/profissional/cliente/status; transições `confirmar` →
  `iniciar` → `finalizar`, além de `nao-compareceu` e `cancelar` (motivo
  obrigatório, auditoria em toda alteração).
- Máquina de estados: `AGENDADO → CONFIRMADO → EM_ATENDIMENTO →
  FINALIZADO`, com `CANCELADO`/`NAO_COMPARECEU` como saídas a partir de
  `AGENDADO`/`CONFIRMADO`/`EM_ATENDIMENTO`.
- **Dupla camada contra sobreposição:** validação em Java (mensagens de
  erro legíveis) **e** constraint de exclusão no Postgres (`EXCLUDE USING
  gist`, extensão `btree_gist`) como garantia real contra concorrência —
  testado diretamente com duas transações inserindo o mesmo horário sem
  leitura prévia entre elas: exatamente uma é aceita, sempre.
- Snapshot de preço e duração do serviço no momento do agendamento (não
  altera retroativamente agendamentos já criados quando um serviço muda de
  preço).
- Frontend: tela **Agenda** com visão dia (colunas por profissional,
  clique para criar, arrastar para remarcar) e visão semana.

`git commit` de referência: `0.4.0`.

### FASE 5 — Comanda, pagamento, caixa, estoque e financeiro [AMPLIADO, sub-entregas] ✅

**Objetivo:** registrar o que de fato aconteceu e quanto entrou. Fase
crítica e antecedente a qualquer automação — sem dados financeiros
confiáveis não há dashboard nem relatório que sirva para decisão.

**Sub-entregas:**

#### 5A — Comanda, caixa e formas de pagamento

- Fluxo no painel: `CONFIRMADO` → `EM_ATENDIMENTO` → `FINALIZADO`, com
  marcação de `NAO_COMPARECEU`.
- Comanda: adicionar/remover serviços e produtos, desconto com motivo
  (rateado proporcionalmente entre os itens), forma de pagamento,
  fechamento.
- Cálculo de comissão por profissional (configurável por serviço).
- Tela **Caixa do dia**: total, por forma de pagamento, por profissional.
- Comanda fechada é imutável — correção apenas por estorno com
  justificativa e auditoria.

#### 5B — Produtos e estoque

- CRUD de produtos (preço de venda, custo, categoria, unidade, estoque
  mínimo).
- Entrada de estoque (compra/ajuste) com custo unitário.
- Baixa automática ao fechar comanda com produto.
- Devolução de estoque no estorno da comanda.
- Saldo calculado a partir de `movimento_estoque`; extrato de
  movimentações por produto.
- Alerta de estoque mínimo no dashboard e listagem "produtos a repor".
- Inventário/ajuste manual com motivo obrigatório e auditoria.

#### 5C — Contas a pagar/receber e fluxo de caixa

- Entidade `Despesa`: data, categoria, valor, descrição, comprovante.
- Entidade `ContaReceber`: cliente, valor, data de vencimento, status.
- Entidade `ContaPagar`: fornecedor/descrição, valor, data de vencimento,
  status.
- Cálculo de fluxo de caixa: caixa atual + contas a receber esperadas −
  contas a pagar vencidas.
- Tela de gestão e reconciliação.

**Critérios de aceite**

- [x] Fecho uma comanda e o valor entra no caixa do dia.
- [x] Desconto de 10% recalcula corretamente total e comissão.
- [x] Editar comanda fechada é bloqueado com mensagem clara.
- [x] Estorno gera registro de auditoria com o usuário responsável e devolve
  o produto ao estoque.
- [x] Vender produto com saldo 0 é bloqueado com mensagem clara.
- [x] Vender 2 unidades de um produto com 5 em estoque deixa saldo 3, e o
  extrato mostra o movimento ligado à comanda.
- [x] Produto abaixo do estoque mínimo aparece no alerta.
- [x] No-show não gera receita mas conta na estatística.
- [x] Despesa é registrada e reduz o resultado do caixa.
- [x] Conta a receber (cliente com débito) pode ser lançada e recuperada.
- [x] Fluxo de caixa mostra: caixa em mãos + a receber − a pagar.

**Nota — identidade visual (fora do escopo original desta fase, registrado
para rastreabilidade):** durante o desenvolvimento da Fase 5, foi aplicado
ao painel administrativo um primeiro rascunho de identidade visual, em PRs
próprios (não fazem parte dos critérios de aceite acima): paleta extraída
por amostragem de pixel da logo CP Desenv (Material 3, `$azure-palette` +
`$blue-palette`), menu lateral e topbar unificados numa faixa azul-marinho
com vinheta radial, tipografia Inter com títulos em Georgia, layout de
tabela em estilo planilha (`.tabela-dados`) aplicado às telas com listagem,
badges em pílula e responsividade mobile (menu em painel deslizante abaixo
de 840px). Toda escolha de cor foi conferida por cálculo de contraste WCAG
AA, não só por inspeção visual. **Este rascunho é provisório** — a
validação formal com o cliente e a consolidação da identidade definitiva
acontecem no **CHECKPOINT-VISUAL** (ver abaixo, antes da Fase 19).

`git commit -m "feat: implementa comanda, formas de pagamento, caixa diario, controle de estoque e gestao financeira"`

### FASE 6 — Comprovante de serviço (PDF) ✅

**Objetivo:** entregar algo útil ao cliente imediatamente, sem depender de
burocracia municipal.

**Entregáveis**

- `FiscalGateway` (interface) com `emitirNotaFiscal()`,
  `consultarNotaFiscal()`, `cancelarNotaFiscal()` — primeira implementação:
  `ReciboFiscalGateway`.
- Geração de PDF (OpenPDF ou iText; evite depender de browser headless) com
  logo, dados da barbearia, dados do cliente, itens (serviços e produtos),
  valores, forma de pagamento, data e número sequencial.
- Armazenamento em storage de objetos (MinIO/R2/S3).
- Envio automático pelo canal de mensageria como documento após o
  fechamento da comanda (via `WhatsAppGateway` — com o mock, o "envio" fica
  registrado e o PDF baixável pelo painel), e por e-mail se houver.
- Botão de reenviar/baixar no painel.

**Critérios de aceite**

- [x] Fecho a comanda e o comprovante é gerado e anexado à conversa do
  cliente.
- [x] O PDF abre corretamente no celular e no desktop, com todos os dados
  corretos.
- [x] Numeração sequencial sem buracos nem duplicidade (teste concorrente
  obrigatório).
- [x] Consigo reenviar o comprovante pelo painel.
- [x] Trocar a implementação do `FiscalGateway` não exige tocar no fluxo de
  comanda.

`git commit -m "feat: implementa emissao de comprovante em PDF e envio ao cliente"`

### FASE 7 — Clube Cavalinho / Assinaturas [NOVO] ✅

**Objetivo:** modelo de negócio de receita recorrente. Crítico para Cortes
Cavalinho.

**Racional:** o Clube Cavalinho é um diferencial estratégico e deve estar
pronto ANTES de automações, para que a IA saiba tratar clientes assinantes
vs. avulsos.

**Entregáveis**

- Entidade `PlanoAssinatura`: nome, preço mensal, benefícios (quantidade de
  cortes, serviços inclusos, desconto %), renovação automática.
- Entidade `Assinatura`: plano, cliente, data de início, data de renovação,
  status (`ATIVA`, `CANCELADA`, `INADIMPLENTE`, `SUSPENSA`), histórico de
  pagamento.
- Vinculação: um cliente pode ter uma assinatura ativa.
- **Regra:** agendamento de cliente assinante consome saldo de cortes; se
  zerado, serviço fica "adicional" (fora da assinatura).
- Comanda com flag `servicoAssinatura` vs. `servicoAdicional`; relatório
  que diferencia receita.
- Renovação automática com retry de pagamento; notificação de
  inadimplência.
- Cancelamento com motivo e data de efeito.
- Painel: listar assinantes ativos, cancelados, inadimplentes, receita
  recorrente.

**Critérios de aceite**

- [x] Cliente assinante agenda serviço: saldo de cortes diminui.
- [x] Saldo zerado: próximo serviço é cobrado como adicional.
- [x] Renovação ocorre no dia configurado.
- [x] Falha de cobrança gera notificação e retry automático.
- [x] Relatório diferencia receita de assinatura de avulso.
- [x] Testes de concorrência: dois agendamentos simultâneos não consomem
  dois saldos.

`git commit -m "feat: implementa Clube Cavalinho com assinaturas e receita recorrente"`

### FASE 8 — Integração com Google Calendar ✅

**Objetivo:** a barbearia enxerga a agenda no celular, no app que já usa.

**Entregáveis**

- Tela de Integrações → botão "Conectar Google Calendar", fluxo OAuth2,
  refresh token armazenado criptografado, renovação automática do access
  token.
- Criação de evento ao confirmar agendamento: título `Serviço — Nome do
  Cliente`, descrição com telefone e observações, horário correto no fuso,
  `googleEventId` salvo no banco.
- Configurável: um calendário por profissional ou calendário único com
  cores.
- Atualização do evento ao remarcar; remoção/marcação ao cancelar.
- Padrão outbox: falha na chamada ao Google não pode derrubar o
  agendamento — enfileira e retenta com backoff; painel sinaliza
  agendamentos fora de sincronia.
- Botão "Ressincronizar agenda".
- `CalendarGateway` mock, para testes e ambiente dev sem conta Google.

**Critérios de aceite**

- [~] Conecto a conta Google pelo painel em menos de 1 minuto.
- [~] Agendamento criado aparece no Google Calendar em até 10 segundos.
- [~] Remarcar move o evento; cancelar remove o evento.
- [x] Com a rede para o Google derrubada, o agendamento continua sendo
  criado e sincroniza sozinho quando volta.
- [~] Token expirado renova sozinho, sem intervenção.
- [x] Nenhum token aparece em log.
- [x] A suíte de testes passa sem nenhuma credencial Google configurada.

**Nota (registrada em 2026-08-27):** os 4 itens marcados `[~]` foram
validados apenas com o `MockCalendarGateway`/`MockGoogleOAuthGateway`
(fluxo completo testado em automação e manualmente no navegador) — não
há ainda um projeto Google Cloud com credenciais OAuth2 reais para
validar contra a API de verdade. O código de ativação real
(`GoogleCalendarGateway`/`GoogleOAuthGatewayImpl`) já está implementado
e coberto por `@ConditionalOnProperty(app.calendar.gateway=google)`;
falta apenas configurar `GOOGLE_CALENDAR_CLIENT_ID`/`_SECRET` e
revalidar esses 4 itens manualmente quando a conta existir.

`git commit -m "feat: integra agendamentos com Google Calendar via OAuth2 e outbox"`

### FASE 9 — Canal de mensageria com MockWhatsAppGateway (sem conta Meta, sem IA)

**Objetivo:** ter o canal de mensagens inteiro funcionando — conversas,
mensagens, idempotência, processamento assíncrono, painel — usando
exclusivamente um gateway simulado.

**Restrição explícita:** não há conta Meta. Nesta fase, implementar apenas
o `MockWhatsAppGateway`. Não criar `CloudApiWhatsAppGateway`, não escrever
cliente da Graph API, não adicionar dependência/variável de ambiente de
token da Meta, e não deixar o sistema em estado que exija credencial para
funcionar — isso é assunto da Fase 6-META, sob pedido explícito. O que
precisa existir aqui é a interface `WhatsAppGateway` bem desenhada, para
que a implementação real seja depois apenas uma classe nova.

**Entregáveis**

- `WhatsAppGateway` (interface) com `sendMessage()`, `sendTemplate()`,
  `sendInteractive()`, `sendDocument()` — assinaturas pensadas para a Cloud
  API caber sem mudança de contrato.
- `MockWhatsAppGateway` como única implementação (`@ConditionalOnProperty`,
  `whatsapp.gateway=mock` por padrão):
  - "envia" persistindo a mensagem no banco com status simulado
    (`ENVIADA` → `ENTREGUE` → `LIDA`, delay configurável);
  - simula falha de envio quando configurado, para exercitar outbox e
    retentativas;
  - registra tudo em log estruturado.
- Simulador de conversa (a parte mais importante desta fase):
  - endpoint interno autenticado `POST /api/dev/whatsapp/inbound` que
    injeta mensagem de entrada como se viesse do provedor;
  - tela "Simulador de WhatsApp" no painel (só dev/staging): telefone,
    texto, histórico em formato de chat.
- Webhook no formato de payload da Cloud API, mesmo sem provedor real:
  - `GET /api/webhook/whatsapp` (verificação, `hub.challenge`);
  - `POST /api/webhook/whatsapp` (recebimento);
  - validação da assinatura `X-Hub-Signature-256` — requisição não assinada
    é rejeitada com 403; segredo local em dev, testes provam a validação
    com payloads assinados de exemplo.
- Idempotência por `waMessageId` — processar a mesma mensagem duas vezes não
  duplica nada.
- Processamento assíncrono (virtual threads): webhook responde 200
  imediatamente e enfileira.
- Persistência de `Conversa`/`Mensagem`; aba "Conversas" no painel.
- Fluxo de eco simples ("recebi: X") para validar ida e volta.
- Vinculação automática da conversa ao cliente por telefone E.164; cliente
  novo criado como rascunho com origem `WHATSAPP`.
- `docs/mensageria.md`: como o mock funciona, como simular conversas, e o
  checklist do que falta para a Fase 6-META.

**Critérios de aceite**

- [ ] Conduzo uma conversa inteira pelo simulador do painel, sem nenhuma
  credencial externa.
- [ ] `grep` no projeto não encontra token, número de telefone da Meta ou
  URL da Graph API.
- [ ] O eco responde e a conversa aparece no painel, vinculada ao cliente
  correto.
- [ ] Webhook com assinatura inválida retorna 403 e não processa nada.
- [ ] Reenvio do mesmo payload (mesmo `waMessageId`) não duplica mensagem no
  banco.
- [ ] Falha simulada de envio cai no outbox e é retentada.
- [ ] Rate limiting aplicado no webhook e no endpoint de injeção.
- [ ] O endpoint `/api/dev/**` e a tela do simulador estão desabilitados no
  perfil `production` — teste automatizado prova isso.
- [ ] Toda a suíte roda no CI sem segredo nenhum.

`git commit -m "feat: implementa canal de mensageria com gateway mockado e simulador de conversas"`

### FASE 6-META — Ativação da WhatsApp Cloud API (adiada; executar só quando o cliente avisar)

*Sem conteúdo original disponível no repositório (placeholder "idêntico à
V3" no prompt de origem) e sem implementação ainda — nada a reconstruir a
partir do README/CHANGELOG. Fica registrada como pendência a especificar em
detalhe no momento em que a conta Meta Business for aprovada.*

**O que se sabe hoje, pelo restante das fases (9, 14):**

- Implementação real de `WhatsAppGateway`
  (`CloudApiWhatsAppGateway`), plugada por configuração, sem alterar o
  contrato já definido na Fase 9.
- Depende de: conta Meta Business + número de telefone dedicado
  (não pode estar ativo no app WhatsApp comum), verificação do negócio na
  Meta, templates de mensagem submetidos para aprovação (ver Seção 9 —
  Providências).
- `docs/mensageria.md` (criado na Fase 9) já deve trazer o checklist do que
  falta fazer nesta fase — usar como ponto de partida quando ela for
  aberta.

Antes de iniciar esta fase de fato, escrever um plano de entregáveis e
critérios de aceite específicos, com base no estado real do
`WhatsAppGateway` naquele momento.

### FASE 10 — Agente de IA: atendimento e agendamento

**Objetivo:** substituir o eco por uma conversa real que agenda de
verdade. Toda a fase é desenvolvida e validada contra o
`MockWhatsAppGateway`, pelo simulador do painel.

**Arquitetura obrigatória:** o LLM não decide disponibilidade, preço, nem
grava nada sozinho. Ele conversa e chama *tools* expostas pelo backend.
Toda regra de negócio permanece em Java.

**Tools disponíveis ao agente:**

```text
consultar_servicos()                                    → lista com preço e duração
consultar_profissionais()                               → barbeiros ativos
consultar_disponibilidade(data, servicos[], profissional?) → slots reais
identificar_cliente(telefone)                           → cliente ou "novo"
cadastrar_cliente(nome, telefone, ...)
criar_agendamento(clienteId, profissionalId, servicos[], inicio)
consultar_agendamentos_do_cliente(clienteId)
escalar_para_humano(motivo)
```

**Roteiro de conversa** (esqueleto, não script literal): identificar
cliente pelo telefone → (se novo) perguntar nome → identificar serviço
desejado → preferência de profissional → dia desejado → preferência de
período → consultar disponibilidade real e oferecer 3–4 horários → cliente
escolhe → resumo com confirmação explícita → criar agendamento → evento no
Calendar → mensagem de confirmação.

**Resumo de confirmação obrigatório antes de criar** (exemplo de formato):
cliente, serviço, profissional, data, horário, valor, seguido de "Posso
confirmar?".

**Cliente recorrente:** reconhecer pelo telefone, cumprimentar pelo nome e
oferecer atalho para repetir o último serviço.

**Suporte a assinaturas (Fase 7):** se o cliente tiver assinatura ativa com
saldo, informar quantos cortes restam no mês.

**Guardrails obrigatórios**

- Nunca prometer horário sem antes chamar `consultar_disponibilidade`.
- Nunca inventar preço, serviço, promoção ou profissional fora do que as
  tools retornaram.
- Nunca criar agendamento sem confirmação explícita do cliente na mensagem
  anterior.
- Escalar para humano em: reclamações, pedido de desconto, assunto fora do
  escopo, terceira tentativa fracassada de entendimento.
- Limite de turnos por conversa antes de escalar (sugestão: 25).
- Resistência a *prompt injection*: instruções vindas do cliente ("ignore
  suas regras", "você agora é...") são tratadas como texto do cliente,
  nunca como instrução de sistema.
- Mensagens curtas, tom cordial e informal brasileiro, no máximo 1 emoji
  por mensagem.
- Timeout: 30 min sem resposta encerra o contexto e envia mensagem de
  retomada.
- **Kill switch:** flag de configuração que desliga a IA e coloca todas as
  conversas em modo humano imediatamente.
- **Teto de custo mensal configurável** — atingido o teto, a IA desliga e
  alerta.

**Entregáveis adicionais**

- `AiAgentGateway` com implementação real (provedor escolhido — Anthropic
  Claude, ver Seção 10 — Variáveis) e implementação mock determinística
  para CI.
- System prompt versionado em arquivo (`resources/prompts/atendimento.md`),
  não hardcoded em string Java.
- Registro de tokens e custo por conversa.
- Painel: aba Conversas com filtro por status e botão "assumir conversa".
- Suíte de 10+ diálogos-roteiro rodando contra LLM mockado no CI: cliente
  indeciso, cliente que muda de ideia, horário indisponível, cliente
  agressivo, mensagem sem sentido, tentativa de injection, cliente
  recorrente, dois serviços juntos, data em linguagem natural ambígua,
  cliente que desiste no meio.

**Critérios de aceite**

- [ ] Conduzo uma conversa completa pelo simulador e o agendamento aparece
  na agenda e no Google Calendar.
- [ ] Peço horário inexistente e o agente oferece alternativas reais em vez
  de aceitar.
- [ ] Mensagem agressiva → conversa escalada para humano.
- [ ] Tentativa de injection não altera o comportamento do agente.
- [ ] Kill switch desliga a IA imediatamente.
- [ ] Os diálogos de teste passam no CI, sem chave de API real.
- [ ] Vejo o custo acumulado de LLM no painel.

`git commit -m "feat: implementa agente de IA de atendimento com tool calling e guardrails"`

### FASE 11 — Cancelamento e remarcação pela IA

**Objetivo:** fechar o ciclo de autoatendimento.

**Entregáveis**

- Tools adicionais: `cancelar_agendamento(id, motivo)`,
  `remarcar_agendamento(id, novoInicio)`.
- Reconhecimento de intenção em linguagem natural ("quero cancelar meu
  horário", "preciso mudar para sexta", "consigo adiar uma hora?").
- Se o cliente tiver mais de um agendamento futuro, o agente pergunta qual.
- Confirmação explícita obrigatória antes de qualquer alteração ou
  cancelamento.
- Política de cancelamento configurável (ex.: até 2h antes; abaixo disso,
  escala para humano).
- Sincronização automática com Google Calendar.
- Registro em auditoria com origem `WHATSAPP` e o texto que motivou a ação.

**Critérios de aceite**

- [ ] Cancelo pelo simulador, o slot é liberado e o evento sai do Calendar.
- [ ] Remarco e o agente oferece apenas horários realmente livres.
- [ ] Cliente com dois agendamentos é questionado sobre qual deles.
- [ ] Nenhuma alteração acontece sem confirmação explícita.
- [ ] Cancelamento fora da política é escalado para humano, não recusado
  secamente.

`git commit -m "feat: permite cancelamento e remarcacao de agendamentos pela conversa"`

### FASE 12 — Link de autoagendamento [NOVO]

**Objetivo:** cliente acessa URL pública e agenda sem passar pela IA.
Canal valioso de conversão.

**Entregáveis**

- Página pública (sem autenticação) com seletor: serviço → profissional →
  data → horário.
- Consulta em tempo real do `AvailabilityService` (já existe, Fase 4).
- Após confirmação, cria agendamento como `CONFIRMADO` e envia confirmação
  por WhatsApp (se celular fornecido).
- Botão de compartilhar link no painel e em cada conversa da IA.
- Analytics: rastrear quantos agendamentos vêm do link vs. WhatsApp vs.
  painel.

**Critérios de aceite**

- [ ] Link abre em celular e desktop sem layout quebrado.
- [ ] Disponibilidade é consultada em tempo real.
- [ ] Agendamento é criado com status `CONFIRMADO`.
- [ ] Cliente recebe confirmação por WhatsApp ou e-mail.
- [ ] Link pode ser desativado globalmente via configuração.

`git commit -m "feat: implementa link de autoagendamento publico"`

### FASE 13 — Dashboard

**Objetivo:** a tela que o dono abre de manhã.

**Entregáveis**

- Cards: faturamento do dia, faturamento do mês (com % vs. mês anterior),
  atendimentos do dia, ticket médio, taxa de ocupação da agenda.
- Listas: agendamentos de hoje (com status), próximos agendamentos,
  conversas aguardando humano, produtos abaixo do estoque mínimo.
- Gráficos: faturamento dos últimos 12 meses (linha), serviços mais
  vendidos (barras), atendimentos por profissional (barras), distribuição
  por forma de pagamento (rosca).
- Indicadores de saúde: clientes novos no mês, cancelamentos, faltas,
  agendamentos fora de sincronia com o Calendar.
- Indicadores de assinatura (Fase 7): receita recorrente, taxa de churn.
- Atualização automática a cada N segundos ou botão de refresh.

**Critérios de aceite**

- [ ] Abro o dashboard e entendo o dia em menos de 10 segundos.
- [ ] Os números batem com o caixa do dia e a agenda.
- [ ] Dashboard carrega em menos de 1,5 s.
- [ ] Layout funciona bem em celular.

`git commit -m "feat: implementa dashboard administrativo com indicadores e graficos"`

### FASE 14 — Automações de retenção [AMPLIADO]

**Objetivo:** reduzir no-show e trazer o cliente de volta.

**Entregáveis**

- Lembrete automático 24h e 2h antes, com botões "Confirmar" e "Cancelar",
  modelado como template (nome + parâmetros) desde já, porque fora da
  janela de 24h da Meta só template funciona.
- Marcação automática de `NAO_COMPARECEU` X minutos após o horário sem
  check-in.
- Campanha de reativação: cliente sem retorno há mais de N dias.
- CRM básico: histórico de interações, tags (VIP, em risco, novo), notas
  do atendente.
- Reativação: mensagem "sentimos sua falta" com cupom de desconto
  (opcional).
- Mensagem de aniversário.
- Painel de automações com liga/desliga por regra e histórico de disparos.
- Opt-out obrigatório: cliente que responde "PARAR" nunca mais recebe
  automação, mas continua conseguindo agendar.

**Critérios de aceite**

- [ ] O lembrete é disparado no horário certo (verificável no histórico de
  disparos e no simulador) e "Confirmar" muda o status no painel.
- [ ] Respondo "PARAR" e paro de receber automações, mas ainda consigo
  agendar.
- [ ] Desligar a automação no painel a interrompe imediatamente.
- [ ] Nenhuma automação dispara para cliente sem `optInWhatsapp`.
- [ ] Cliente sem retorno há 30 dias entra em lista de recuperação.
- [ ] Mensagem de reativação é enviada (com opt-out).
- [ ] Painel exibe clientes em risco de saída.

`git commit -m "feat: implementa lembretes automaticos, no-show, CRM basico e campanhas com opt-out"`

### FASE 15 — Relatórios comparativos [AMPLIADO]

**Objetivo:** responder "estou melhorando?" em 10 segundos.

**Entregáveis**

- Comparação entre períodos como recurso central: mês atual vs. mês
  anterior vs. mesmo mês do ano anterior, sempre com variação absoluta e
  percentual.
- Relatórios: faturamento por mês, por serviço, por profissional e por
  forma de pagamento; quantidade de clientes; clientes novos vs.
  recorrentes; taxa de retorno; cancelamentos; não comparecimentos;
  serviços mais realizados; produtos mais vendidos e margem; horários de
  maior movimento (heatmap dia da semana × hora); taxa de ocupação por
  profissional; comissões a pagar.
- Fluxo de caixa mensal: entrada de caixa, contas a receber e a pagar,
  comparação.
- Previsão de compromissos: comissões a pagar, estoque a renovar, contas
  vencidas.
- Análise de assinaturas (Fase 7): receita recorrente vs. avulso, taxa de
  churn, LTV.
- Filtros: data inicial, data final, profissional, serviço, forma de
  pagamento.
- Exportação para Excel e PDF.
- **Performance:** relatórios respondem em < 1 s — usar views
  materializadas ou tabela de agregação diária atualizada por job, nunca
  `SELECT` pesado em tempo real sobre a tabela transacional.

**Critérios de aceite**

- [ ] Vejo agosto vs. julho com variação percentual em cada indicador.
- [ ] Filtro por barbeiro e todos os números recalculam corretamente.
- [ ] Exporto para Excel e os números batem com a tela.
- [ ] Com 50.000 atendimentos de massa de teste, o relatório carrega em
  menos de 1 segundo.
- [ ] Fluxo de caixa mostra: caixa em mãos + a receber − a pagar.
- [ ] Previsão exibe comissões e contas vencidas.

`git commit -m "feat: implementa modulo de relatorios com comparativo mensal, fluxo de caixa e exportacao"`

### FASE 16 — NFS-e real

**Objetivo:** emissão fiscal válida.

**Contexto:** NFS-e é municipal, cada prefeitura tem seu padrão. Não
implementar integração direta com a prefeitura — usar um integrador que
abstrai isso.

**Entregáveis**

- Antes de codar: comparar Focus NFe, PlugNotas, eNotas e NFE.io (preço,
  cobertura do município — Campinas/SP —, qualidade da API, suporte) e
  apresentar recomendação.
- Nova implementação de `FiscalGateway` para o provedor escolhido.
- Configuração em Integrações: upload de certificado digital A1
  (armazenado criptografado), regime tributário, alíquota ISS, código de
  serviço municipal, série, inscrição municipal.
- Emissão assíncrona com máquina de estados: `PENDENTE` → `PROCESSANDO` →
  `AUTORIZADA` | `REJEITADA` (motivo legível) | `CANCELADA`.
- Retentativa automática em falha transitória; fila de rejeições para
  correção manual no painel.
- Cancelamento de nota dentro do prazo legal.
- Envio do PDF/link ao cliente pelo canal de mensageria.
- Ambientes `HOMOLOGACAO` e `PRODUCAO` configuráveis — testar tudo em
  homologação primeiro.
- `docs/fiscal-setup.md`: o que a barbearia precisa providenciar
  (certificado A1, inscrição municipal, liberação na prefeitura).

**Critérios de aceite**

- [ ] Emito nota em homologação e recebo `AUTORIZADA` com link do PDF.
- [ ] Rejeição aparece no painel com mensagem traduzida e botão de
  reemitir.
- [ ] Cancelamento funciona e reflete no status.
- [ ] Nenhum dado do certificado aparece em log.
- [ ] Alternar homologação/produção é uma configuração, não um deploy.

`git commit -m "feat: integra emissao de NFS-e com provedor fiscal e ambiente de homologacao"`

### FASE 17 — Gestão de estoque avançada [NOVO]

**Objetivo:** rastreabilidade completa de produtos: entrada, saída,
alertas, histórico.

**Entregáveis** (amplia a base já entregue na Fase 5B)

- Entrada/saída de estoque com histórico completo e trilha de auditoria.
- Alertas automáticos quando estoque cai abaixo do mínimo.
- Relatório de produtos com estoque baixo.
- Movimentação por período, margem de lucro por produto.
- Inventário de ajuste (diferença entre esperado e real).

**Critérios de aceite**

- [ ] Compra de 10 unidades de produto eleva o estoque em 10.
- [ ] Comanda com 2 produtos reduz estoque automaticamente.
- [ ] Histórico exibe todas as movimentações com usuário e motivo.
- [ ] Alerta dispara quando estoque atinge o mínimo.
- [ ] Relatório mostra valor total em estoque e margem por produto.

`git commit -m "feat: amplia gestao de estoque com alertas, historico e margem"`

### FASE 18 — Produção, observabilidade e LGPD

**Objetivo:** colocar no ar com segurança e conseguir dormir à noite.

**Entregáveis**

**Segurança:** HTTPS com certificado automático, security headers (HSTS,
CSP, X-Frame-Options), CORS restrito; rate limiting em webhook, login,
portal público e endpoints públicos; revisão de dependências (OWASP
Dependency-Check no CI); rotação documentada de tokens e segredos.

**Observabilidade:** logs estruturados em JSON com `traceId`
correlacionando mensagem/portal → agente → agendamento → Calendar → nota;
Actuator + Prometheus + Grafana (ou alternativa gerenciada); alertas
(falha no webhook, fila outbox travada, erro de emissão fiscal, custo de
LLM acima do teto, backend fora do ar); métrica explícita de qual gateway
de mensageria está ativo (mock ou cloud-api), visível no painel.

**Continuidade:** backup automatizado do Postgres com retenção definida;
teste de restore executado e documentado ao menos uma vez; ambiente de
staging separado; `docs/runbook.md` (deploy, rollback, restaurar backup,
rotacionar tokens, o que fazer quando a mensageria para de responder).

**LGPD (consolidação do que já foi construído):** política de privacidade
e consentimento registrado no primeiro contato (mensageria e portal);
endpoint de exportação e de exclusão/anonimização dos dados do cliente
(já existe desde a Fase 3 — revisar cobertura); política de retenção do
histórico de conversas; auditoria de acesso a dados pessoais; logs sem
exposição de dado sensível (revisão final).

**Critérios de aceite**

- [ ] Deploy completo em servidor limpo seguindo apenas o runbook, sem
  ajuda externa.
- [ ] Restauro um backup em ambiente limpo e o sistema volta íntegro.
- [ ] Derrubo o backend e o alerta dispara.
- [ ] Rollback para a versão anterior em menos de 5 minutos.
- [ ] Requisição de exclusão de dados de um cliente é atendida sem quebrar
  registros fiscais.
- [ ] Varredura de logs não encontra CPF, token ou senha.
- [ ] O painel mostra claramente que a mensageria está em modo mock.

`git commit -m "feat: prepara ambiente de producao com observabilidade, backup e conformidade LGPD"`

### CHECKPOINT-VISUAL — Validação de identidade visual com o cliente (fora da sequência numerada, antes da Fase 19)

**Objetivo:** validar formalmente com o cliente (Cortes Cavalinho) o
rascunho de identidade visual aplicado ao painel administrativo durante a
Fase 5, antes de propagar esse padrão para telas voltadas ao cliente final
(portal público da Fase 19, comprovante da Fase 6, NFS-e da Fase 16).

**Contexto:** o rascunho atual (paleta extraída da logo CP Desenv, menu
lateral e topbar em azul-marinho, tipografia, layout de tabelas) foi
implementado sem validação formal do cliente — só para haver algo
apresentável durante o desenvolvimento.

**Entregáveis**

- Apresentação do painel atual ao cliente.
- Coleta de feedback objetivo: paleta de cores, uso da logo, tipografia,
  tom geral.
- Ajustes solicitados pelo cliente aplicados e revalidados.
- Decisões finais registradas em `docs/identidade-visual.md` (paleta
  aprovada, fontes, regras de uso da logo), como referência para a Fase
  19.

**Critérios de aceite**

- [ ] Cliente validou a paleta de cores e o estilo geral do painel (ou
  solicitou ajustes específicos, já aplicados).
- [ ] `docs/identidade-visual.md` documenta as decisões finais aprovadas.
- [ ] A Fase 19 pode ser executada usando essa documentação como
  referência, sem nenhuma decisão de identidade visual pendente.

`git commit -m "docs: registra identidade visual validada com o cliente"`

### FASE 19 — Portal público de autoagendamento

**Objetivo:** dar ao cliente um canal de agendamento que não depende da
Meta: um link público onde ele escolhe serviço, profissional e horário
sozinho.

**Pode ser antecipada** — depende apenas do motor de disponibilidade
(Fase 4, já pronto) e do cadastro de clientes (Fase 3, já pronto). Pode ser
executada logo após a Fase 8, se um canal de autoatendimento real antes da
IA for prioridade.

**Entregáveis**

- Área pública no mesmo app Angular (`/agendar`), sem login, responsiva e
  pensada para celular primeiro.
- Fluxo em passos: escolher serviço(s) → profissional (ou "qualquer um") →
  data → horário (slots do mesmo `AvailabilityService`, sem lógica
  duplicada) → nome e telefone → confirmar.
- Identificação por telefone + código de verificação enviado pelo canal de
  mensageria (com o mock, o código aparece no painel/simulador e em log em
  dev). Sem senha, sem cadastro.
- Cliente recorrente reconhecido pelo telefone; nome já preenchido.
- Página "meus agendamentos" acessada pelo mesmo código: ver, cancelar e
  remarcar respeitando a política configurada.
- Agendamento criado com origem `PORTAL`, entrando na mesma agenda, no
  mesmo Google Calendar e na mesma auditoria.
- Consentimento LGPD explícito no primeiro agendamento, com link para a
  política.
- Proteções: rate limiting por IP e por telefone, expiração e limite de
  tentativas do código, antibot (honeypot ou captcha), limite de
  agendamentos futuros por cliente, bloqueio de telefones abusivos.
- Configurável no painel: portal ligado/desligado, antecedência mínima e
  máxima para agendar, serviços visíveis ao público, profissionais
  visíveis, texto de boas-vindas.
- Tela de confirmação com resumo e opção de adicionar ao calendário do
  cliente (arquivo `.ics`).

**Critérios de aceite**

- [ ] Agendo pelo celular, sem login, em menos de 1 minuto, e o agendamento
  aparece na agenda do painel.
- [ ] Os horários oferecidos são exatamente os que o painel considera
  livres — mesmo serviço, mesmo profissional, mesmo dia.
- [ ] **Teste de concorrência:** portal e painel disputando o mesmo slot →
  um sucede, o outro recebe 409 com mensagem amigável e slots atualizados.
- [ ] Cancelo pelo portal e o slot é liberado, o evento sai do Calendar e a
  auditoria registra origem `PORTAL`.
- [ ] Código de verificação errado ou expirado não permite acesso a
  agendamento nenhum.
- [ ] Não consigo ver dados de outro cliente trocando telefone ou ID na URL
  (teste automatizado de autorização).
- [ ] Serviço marcado como não visível ao público não aparece no portal.
- [ ] Desligar o portal no painel derruba a rota pública imediatamente.
- [ ] Lighthouse mobile ≥ 90 em performance e acessibilidade.

`git commit -m "feat: implementa portal publico de autoagendamento com verificacao por codigo"`

## 8. Interface Angular — menu

```text
Dashboard

Agenda

Clientes

Profissionais

Serviços

Produtos
├── Catálogo
└── Estoque e movimentações

Financeiro
├── Caixa do dia
├── Comandas
├── Comissões
├── Contas a pagar/receber    [NOVO]
└── Fluxo de caixa            [NOVO]

Clube Cavalinho               [NOVO]
├── Meus planos
├── Assinantes
└── Análise de receita recorrente

Notas Fiscais

Relatórios

Conversas
├── Histórico
└── Simulador (apenas dev/staging)

Integrações
├── Mensageria (WhatsApp)
├── Google Calendar
├── Inteligência Artificial
└── Emissão Fiscal

Configurações
├── Barbearia
├── Portal de agendamento
├── Usuários
├── Horários de funcionamento
├── Automações
└── Auditoria
```

**Área pública, fora do menu autenticado:** `/agendar` (portal de
autoagendamento).

Interface moderna, responsiva e utilizável em celular — o dono da barbearia
vai consultar a agenda pelo telefone com muito mais frequência do que pelo
computador, e o cliente vai agendar pelo celular quase sempre.

## 9. Estratégia de implantação

*Reconstruído a partir do `README.md` § "Estratégia de implantação"
(resolvida na prática — a Seção 8 do prompt original só pedia uma
comparação de opções).*

VPS única com Docker Compose. Traefik + Let's Encrypt para HTTPS
automático, backup automatizado do Postgres, deploy via GitHub Actions +
SSH — detalhado na Fase 18.

## 10. O que o cliente precisa providenciar, por fase

- **Fases 0 a 4:** nada além de Docker instalado. É proposital.
- **Fase 8:** conta Google + projeto no Google Cloud Console com Calendar
  API habilitada e tela de consentimento OAuth configurada.
- **Fase 10:** chave de API do provedor de LLM, com limite de gasto
  configurado.
- **Fase 19:** domínio registrado e conta no provedor de hospedagem
  escolhido; logo da barbearia e dados cadastrais completos.
- **Só quando o cliente decidir ativar o WhatsApp real (Fase 6-META), sem
  pressa:** conta Meta Business + número de telefone dedicado (não pode
  estar ativo no app WhatsApp comum); verificação do negócio na Meta;
  templates de mensagem submetidos para aprovação (⏳ dias).
- **Só na Fase 16:** certificado digital A1 e inscrição municipal (⏳ dias a
  semanas).

## 11. Variáveis do projeto

*Valores confirmados a partir do repositório (migration `V6` + seed `V12`,
`README.md`) — não são apenas exemplos do prompt original.*

| Variável | Valor | Fonte |
|---|---|---|
| Nome da barbearia | Cortes Cavalinho | Seed dev (`V12__semeia_dados_de_exemplo.sql`), usado em todo o texto do produto |
| Cidade/UF | Campinas/SP | Seed dev (`V12`) |
| Fuso horário | America/Sao_Paulo | Default da coluna `fuso_horario` (`V6__cria_tabela_barbearia.sql`) |
| Nº de profissionais (referência) | 3 | Seed dev (`V12`) — número real pode variar em produção |
| Provedor de LLM | Anthropic Claude | `README.md` (Integrações), ordem de preferência na Fase 10 |
| Orçamento mensal de infra | *a confirmar com o cliente* | Não há evidência técnica no repositório — valor de referência do prompt original era "até R$ 150/mês" |
| Volume de atendimentos/mês | *a confirmar com o cliente* | Não há evidência técnica no repositório — valor de referência do prompt original era "600" |
| Domínio do portal público | *a confirmar* — plausível `agendar.cortescavalinho.com.br`, dado o domínio de e-mail já usado (`cortescavalinho.com.br`) | Seed dev (`V12`, campo `email`); domínio final ainda não registrado |

**Já resolvidos, não reabrir:** Java 21 + Spring Boot 3.5.x, barbearia
única (sem multi-tenant), mensageria em mock até a Fase 6-META.

## 12. Ajustes opcionais de escopo

- **Para um MVP mais rápido:** cortar as Fases 16 (NFS-e) e 17 (estoque
  avançado), e reduzir a Fase 15 a três indicadores (faturamento,
  atendimentos, ticket médio). As fases cortadas entram depois sem
  retrabalho, porque a arquitetura já as prevê.
- **Para ter um canal real de agendamento o quanto antes:** executar a
  Fase 19 (portal público) imediatamente após a Fase 8 (Google Calendar).
  Como o WhatsApp está em mock, o portal passa a ser o único canal em que
  o cliente final agenda de verdade — e não depende de aprovação de
  ninguém.
- **Quando a conta Meta sair:** executar a Fase 6-META. Nada do que foi
  construído nas Fases 9 a 19 precisa mudar; a implementação real entra
  como uma classe nova por trás da mesma interface, ativada por
  configuração.
- **Não fazer:** transformar isto em SaaS multi-barbearia. Se essa
  necessidade aparecer, é um novo projeto de arquitetura, com decisão
  consciente de negócio — não uma extensão silenciosa deste.

## 13. Limitações conhecidas

Ver [`docs/limitacoes.md`](limitacoes.md) — decisões de escopo já fechadas
e registradas para não serem reabertas por engano: sem multi-tenant,
mensageria em modo mock até a Fase 6-META, fim de vida open-source do
Spring Boot 3.5.x, e tema visual provisório até o CHECKPOINT-VISUAL.
