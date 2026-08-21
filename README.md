# Sistema para Barbearia

Sistema de gestão para uma barbearia de pequeno/médio porte: agenda,
clientes, serviços, profissionais, caixa, comissões, estoque e (nas fases
seguintes) atendimento automatizado por IA via WhatsApp e portal público de
autoagendamento.

> Escopo fechado: atende **uma única barbearia** — não é multi-tenant e não
> deve ser preparado para virar SaaS. Veja [`docs/limitacoes.md`](docs/limitacoes.md).

## Arquitetura

Monorepo com backend, frontend e infraestrutura desacoplados, tudo
containerizado desde a Fase 0:

```
Sistema Barbearia/
├── backend/    Spring Boot 3.5.16 (Java 21) — API REST
├── frontend/   Angular 22 — painel administrativo + (Fase 16) portal público
├── infra/      docker-compose (dev e prod)
└── docs/       documentação complementar
```

Integrações externas (Google Calendar, WhatsApp, IA, emissão fiscal) ficam
sempre atrás de uma interface (`*Gateway`), com implementação mock disponível
para desenvolvimento e testes, e implementação real plugada apenas quando as
credenciais existirem. Nenhuma fase depende de credencial de terceiro para
ser validada.

## Tecnologias e versões

| Camada | Tecnologia | Versão |
|---|---|---|
| Backend | Java | 21 LTS |
| Backend | Spring Boot | 3.5.16 |
| Frontend | Angular | 22.1.x |
| Banco de dados | PostgreSQL | 17.11 |
| Storage | MinIO (S3-compatível) | latest |

## Requisitos para rodar

- Docker + Docker Compose (única dependência obrigatória para as Fases 0–4)
- Para desenvolvimento **fora** do Docker: JDK 21 e Node.js **22.22.3+ / 24.15+ / 26+**
  (o Angular CLI 22 exige uma dessas versões — Node 18/20 não funcionam)

## Como executar com Docker (recomendado)

```bash
cp .env.example .env
docker compose -f infra/docker-compose.yml --env-file .env up --build
```

- Backend: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health check: http://localhost:8080/api/health e http://localhost:8080/actuator/health
- Frontend: http://localhost:4200
- Console do MinIO: http://localhost:9001

## Como executar localmente (sem Docker)

Backend:

```bash
cd backend
export DB_HOST=localhost DB_PORT=5432 DB_NAME=barbearia DB_USER=barbearia DB_PASSWORD=barbearia
./mvnw spring-boot:run
```

Frontend (requer Node 22.22.3+/24.15+/26+):

```bash
cd frontend
npm install
npm start
```

## Variáveis de ambiente

Veja [`.env.example`](.env.example). Nunca commite o arquivo `.env` real —
segredos sempre via variável de ambiente.

Destaques a partir da Fase 1:

- `JWT_SECRET`: chave usada para assinar os tokens de acesso. Em dev, se
  omitida, um valor padrão inseguro é usado — **gere um valor real com
  `openssl rand -base64 48` antes de ir para produção** (o backend recusa
  subir com uma chave de menos de 32 bytes).
- `ADMIN_EMAIL` / `ADMIN_PASSWORD`: se definidas na primeira subida do banco,
  uma migration cria o usuário administrador inicial com esse e-mail/senha.
  Se omitidas, nenhum administrador é criado automaticamente (é assim que a
  suíte de testes roda, sem depender de nenhuma credencial).

## Autenticação (Fase 1)

Login, renovação de token e logout, testados com o backend rodando (local ou
via Docker):

```bash
# Login — devolve access token (15 min), refresh token (7 dias) e o usuário
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@teste.local","senha":"SenhaAdmin123!"}'

# Renovar (o refresh token antigo é revogado nessa chamada — rotação)
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken recebido no login>"}'

# Logout — revoga o refresh token informado
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken atual>"}'

# Endpoint protegido sem token → 401 JSON; com token, use:
curl http://localhost:8080/api/algum-endpoint-futuro \
  -H "Authorization: Bearer <accessToken recebido no login>"
```

Erros seguem sempre o mesmo formato JSON:
`{"timestamp", "status", "erro", "mensagem", "caminho", "campos"}` — `campos`
só é preenchido em erros de validação (400). O login tem rate limiting (5
tentativas/minuto por IP, configurável em `app.rate-limit.login` no
`application.yml`); ao estourar, a resposta é 429 com
`"erro":"LIMITE_DE_TENTATIVAS_EXCEDIDO"`.

## Clientes (Fase 3)

CRUD de clientes com telefone normalizado para E.164, validação de CPF,
detecção de duplicidade por telefone, ficha com histórico (vazio até as
Fases 4–6 existirem) e conformidade LGPD (consentimento, exportação e
anonimização de dados).

```bash
# Criar cliente — telefone e CPF sao normalizados no backend
curl -X POST http://localhost:8080/api/clientes \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Joao da Silva","telefone":"(19) 99999-8888","cpf":"111.444.777-35","optInWhatsapp":true,"consentimentoLgpd":true}'

# Buscar por nome, telefone ou CPF, com paginacao
curl "http://localhost:8080/api/clientes?busca=joao&page=0&size=20" \
  -H "Authorization: Bearer <accessToken>"

# Ficha do cliente (dados + historico — listas vazias nesta fase)
curl http://localhost:8080/api/clientes/<uuid>/ficha \
  -H "Authorization: Bearer <accessToken>"

# Exportar dados pessoais (LGPD, art. 18) — ADMIN/GERENTE
curl http://localhost:8080/api/clientes/<uuid>/exportar-dados \
  -H "Authorization: Bearer <accessToken>"

# Anonimizar (exclusao LGPD) — mantem a linha, zera os dados pessoais
curl -X POST http://localhost:8080/api/clientes/<uuid>/anonimizar \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"motivo":"Solicitacao do titular"}'
```

Cadastrar com um telefone já existente devolve `409 CLIENTE_DUPLICADO` com os
dados do cliente já cadastrado (`clienteExistente`), para o painel oferecer
abrir o cadastro existente em vez de criar um duplicado — o telefone é único
no banco porque é a chave que a mensageria (Fases 9+) vai usar para
identificar o cliente.

## Agenda e motor de disponibilidade (Fase 4)

O motor de disponibilidade calcula os horários realmente livres de um
profissional (ou de todos os que realizam os serviços pedidos), considerando
grade semanal, bloqueios, agendamentos existentes, duração total dos
serviços e as antecedências mínima/máxima configuradas na barbearia. A
sobreposição de agendamentos para o mesmo profissional é impedida em duas
camadas: uma validação em Java (que dá mensagens de erro legíveis para o
caso comum) e uma **constraint de exclusão no Postgres**
(`EXCLUDE USING gist`, com `btree_gist`) — a garantia real contra duas
requisições concorrentes disputando o mesmo horário.

```bash
# Consultar disponibilidade — servicoUuids aceita uma lista separada por virgula
curl "http://localhost:8080/api/agenda/disponibilidade?data=2026-08-24&servicoUuids=<uuid-servico>&profissionalUuid=<uuid-profissional>" \
  -H "Authorization: Bearer <accessToken>"

# Criar agendamento (nasce com status AGENDADO)
curl -X POST http://localhost:8080/api/agendamentos \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"clienteUuid":"<uuid>","profissionalUuid":"<uuid>","servicoUuids":["<uuid>"],"inicio":"2026-08-24T12:00:00Z","observacao":null}'

# Transicoes de status
curl -X POST http://localhost:8080/api/agendamentos/<uuid>/confirmar -H "Authorization: Bearer <accessToken>"
curl -X POST http://localhost:8080/api/agendamentos/<uuid>/iniciar    -H "Authorization: Bearer <accessToken>"
curl -X POST http://localhost:8080/api/agendamentos/<uuid>/finalizar  -H "Authorization: Bearer <accessToken>"
curl -X POST http://localhost:8080/api/agendamentos/<uuid>/nao-compareceu -H "Authorization: Bearer <accessToken>"
curl -X POST http://localhost:8080/api/agendamentos/<uuid>/cancelar \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"motivo":"Cliente remarcou por telefone"}'

# Remarcar (arrastar na agenda faz essa mesma chamada)
curl -X PUT http://localhost:8080/api/agendamentos/<uuid> \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"clienteUuid":"<uuid>","profissionalUuid":"<uuid>","servicoUuids":["<uuid>"],"inicio":"2026-08-24T14:00:00Z","observacao":null}'
```

Máquina de estados: `AGENDADO → CONFIRMADO → EM_ATENDIMENTO → FINALIZADO`,
com `CANCELADO` e `NAO_COMPARECEU` como saídas a partir de `AGENDADO`/
`CONFIRMADO` (`EM_ATENDIMENTO` também pode ser cancelado). Uma corrida de
concorrência genuína (duas transações inserindo ao mesmo tempo, sem leitura
prévia entre elas) é testada diretamente contra a constraint de exclusão em
`AgendamentoControllerIntegrationTest` — nesse teste, exatamente uma das
duas inserções é aceita, não importa a ordem em que as threads rodem.

No painel, a tela **Agenda** oferece visão dia (colunas por profissional,
clique numa célula vazia para criar, arrastar um agendamento para
remarcar) e visão semana (lista compacta por dia).

## Comanda, caixa e formas de pagamento (Fase 5A)

Uma comanda é sempre aberta a partir de um agendamento: ao clicar em
"Iniciar atendimento" na tela de Agenda, o backend transiciona o
agendamento para `EM_ATENDIMENTO` e cria a comanda `ABERTA` já com os
serviços do agendamento. A partir daí é possível adicionar/remover itens de
serviço, aplicar um desconto (com motivo obrigatório, rateado
proporcionalmente entre os itens) e escolher a forma de pagamento. Ao
fechar a comanda, o agendamento é automaticamente transicionado para
`FINALIZADO` e o valor entra no **Caixa do dia**. Comanda `FECHADA` é
imutável — qualquer correção é feita por **estorno** (motivo obrigatório,
com auditoria), que libera o agendamento para uma nova comanda ser aberta.

A comissão de cada item de serviço é calculada sobre o valor líquido (já
com o desconto rateado), usando o percentual específico do vínculo
profissional↔serviço quando existir, ou o percentual padrão do
profissional caso contrário — recalculada em tempo real a cada mudança de
item ou desconto, para a comanda sempre mostrar quanto o profissional vai
receber.

```bash
# Abrir a comanda de um agendamento CONFIRMADO (idempotente: chamar de novo
# enquanto a comanda estiver ABERTA devolve a mesma comanda)
curl -X POST http://localhost:8080/api/comandas/abrir-para-agendamento/<uuid-agendamento> \
  -H "Authorization: Bearer <accessToken>"

# Adicionar item, aplicar desconto, definir forma de pagamento
curl -X POST http://localhost:8080/api/comandas/<uuid-comanda>/itens \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"servicoUuid":"<uuid>","quantidade":1}'
curl -X PUT http://localhost:8080/api/comandas/<uuid-comanda>/desconto \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"valor":10.00,"motivo":"Cliente fidelidade"}'
curl -X PUT http://localhost:8080/api/comandas/<uuid-comanda>/forma-pagamento \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"formaPagamento":"PIX"}'

# Fechar (transiciona o agendamento para FINALIZADO) e, se preciso, estornar
curl -X POST http://localhost:8080/api/comandas/<uuid-comanda>/fechar   -H "Authorization: Bearer <accessToken>"
curl -X POST http://localhost:8080/api/comandas/<uuid-comanda>/estornar \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"motivo":"Cobranca duplicada"}'

# Caixa do dia (default: hoje)
curl "http://localhost:8080/api/caixa?data=2026-08-24" -H "Authorization: Bearer <accessToken>"
```

## Produtos e estoque (Fase 5B)

Catálogo de produtos (nome, categoria, unidade, preço de venda/custo,
estoque mínimo) e um histórico de movimentações (entrada, saída, ajuste,
devolução) por trás de um saldo em cache (`estoque_atual`), atualizado por
um `UPDATE` atômico que só aplica o delta se o resultado continuar `>= 0` —
protege contra duas baixas concorrentes deixarem o saldo negativo sem
precisar de lock explícito.

A comanda agora aceita itens de produto além de serviço. Produto não gera
comissão, mas entra no rateio do desconto normalmente. A baixa de estoque só
acontece **ao fechar** a comanda (nunca ao adicionar o item): se não houver
saldo suficiente para algum produto, o fechamento inteiro é recusado e nada
é alterado. O estorno devolve a quantidade ao estoque automaticamente.

```bash
# CRUD de produto
curl -X POST http://localhost:8080/api/produtos \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"nome":"Pomada Modeladora","categoria":"Estetica","unidade":"UN","precoVenda":45.00,"precoCusto":20.00,"estoqueMinimo":5}'

# Entrada de estoque (compra) e ajuste manual de inventário (motivo obrigatorio)
curl -X POST http://localhost:8080/api/produtos/<uuid-produto>/entrada-estoque \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"quantidade":10,"custoUnitario":20.00,"motivo":"Compra fornecedor"}'
curl -X POST http://localhost:8080/api/produtos/<uuid-produto>/ajuste-estoque \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"novaQuantidadeContada":8,"motivo":"Contagem de inventario"}'

# Extrato de movimentacoes e alerta de estoque minimo
curl "http://localhost:8080/api/produtos/<uuid-produto>/movimentos" -H "Authorization: Bearer <accessToken>"
curl "http://localhost:8080/api/produtos/alertas-estoque-minimo" -H "Authorization: Bearer <accessToken>"

# Adicionar produto a uma comanda aberta
curl -X POST http://localhost:8080/api/comandas/<uuid-comanda>/itens/produto \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"produtoUuid":"<uuid-produto>","quantidade":2}'
```

## Despesas, contas a pagar/receber e fluxo de caixa (Fase 5C)

Além do Caixa do dia (Fase 5A, que soma só as comandas fechadas *daquele
dia*), o **Fluxo de caixa** (`GET /api/financeiro/fluxo-caixa`) dá a foto
completa da saúde financeira: **caixa em mãos** (todas as comandas
`FECHADA` já lançadas, menos todas as despesas) **+ contas a receber
esperadas** (todo débito de cliente ainda `PENDENTE`, esperado entrar) **−
contas a pagar vencidas** (só as `PENDENTE` cujo vencimento já passou — uma
conta a pagar futura não pesa no fluxo ainda).

```bash
# Lançar uma despesa (ADMIN/GERENTE)
curl -X POST http://localhost:8080/api/despesas \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"data":"2026-08-21","categoria":"Aluguel","valor":1200.00,"descricao":"Aluguel de agosto"}'

# Lançar e liquidar uma conta a pagar (ADMIN/GERENTE)
curl -X POST http://localhost:8080/api/contas-pagar \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"descricao":"Fornecedor de produtos","valor":300.00,"dataVencimento":"2026-08-25"}'
curl -X POST http://localhost:8080/api/contas-pagar/<uuid-conta>/pagar -H "Authorization: Bearer <accessToken>"

# Lançar um debito de cliente (ADMIN/GERENTE/RECEPCAO) e recebe-lo depois (ADMIN/GERENTE)
curl -X POST http://localhost:8080/api/contas-receber \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"clienteUuid":"<uuid-cliente>","descricao":"Corte fiado","valor":50.00,"dataVencimento":"2026-08-30"}'
curl -X POST http://localhost:8080/api/contas-receber/<uuid-conta>/receber -H "Authorization: Bearer <accessToken>"

# Fluxo de caixa consolidado
curl http://localhost:8080/api/financeiro/fluxo-caixa -H "Authorization: Bearer <accessToken>"
```

## Como executar os testes

Backend (usa Testcontainers — requer Docker disponível para o usuário que
executa os testes):

```bash
cd backend
./mvnw test
```

Frontend:

```bash
cd frontend
npm test
```

## Documentação da API

Swagger UI: `/swagger-ui.html` (com o backend rodando). Uma coleção
Postman/Insomnia será commitada a partir da Fase 2, quando os primeiros
endpoints de CRUD existirem.

## Integrações externas e mock ↔ real

| Integração | Mock (padrão em dev/test) | Real | Fase |
|---|---|---|---|
| WhatsApp | `MockWhatsAppGateway` | `CloudApiWhatsAppGateway` (adiada) | 6 / 6-META |
| Google Calendar | `CalendarGateway` mock | OAuth2 + Calendar API v3 | 5 |
| IA | `AiAgentGateway` mock determinístico | Anthropic Claude / OpenAI | 7 |
| Fiscal | Recibo em PDF | Provedor de NFS-e | 10 / 11 |

A troca entre mock e real é sempre por configuração (`application.yml` /
variável de ambiente), nunca por alteração de código de domínio.

## Estratégia de implantação

VPS única com Docker Compose (Opção A — ver comparação completa na
discussão da Fase 0). Traefik + Let's Encrypt, backup automatizado do
Postgres, deploy via GitHub Actions + SSH — detalhado na Fase 15.

## Decisões arquiteturais

- **PostgreSQL**: manutenção simples (`pg_dump`/`pg_restore`), extensões
  maduras (`btree_gist` para a constraint de anti-sobreposição de horários),
  paridade total entre local e produção.
- **BIGINT interno + UUID público**: melhor performance de índice/join
  internamente, sem expor identificadores sequenciais em URLs.
- **Package-by-feature**: cada módulo de negócio (`agenda`, `cliente`,
  `atendimento`...) concentra controller/service/repository/domain/dto —
  reduz o custo de navegação e acoplamento entre camadas transversais.
- **Padrão outbox** para toda chamada externa que pode falhar (Calendar,
  WhatsApp, fiscal): a operação principal nunca é derrubada por uma
  integração fora do ar.
- **Tool/function calling para o agente de IA** (a partir da Fase 7): o LLM
  nunca decide disponibilidade, preço ou grava dado sozinho — toda regra de
  negócio permanece em Java.
- **Refresh token opaco e revogável** (não outro JWT): fica em tabela própria
  com hash SHA-256, é rotacionado a cada uso e pode ser revogado de verdade
  no logout — um JWT de refresh "stateless" não permitiria isso.
- **Rate limiting em memória** (Bucket4j) no login, sem depender de Redis —
  que só entra no projeto a partir da Fase 7 (fila/cache do agente de IA).
- **Proxy `/api` no Nginx (prod) e no `ng serve` (dev)**: o frontend sempre
  fala com o backend pela mesma origem, então não há necessidade de liberar
  CORS.
- Mais detalhes e o raciocínio completo de cada fase ficam registrados nas
  conversas de revisão de cada fase e neste README, à medida que evoluem.

## Limitações conhecidas

Ver [`docs/limitacoes.md`](docs/limitacoes.md) — começando por: não é
multi-barbearia, mensageria roda em mock até a conta Meta existir, e a linha
Spring Boot 3.5.x já está fora do período de suporte open-source.

## Nota sobre o ambiente em que a Fase 0 foi gerada

O projeto Angular desta fase foi montado manualmente (sem `ng new`), porque
o ambiente de desenvolvimento tinha Node 18 (insuficiente para o Angular
CLI 22, que exige Node 22.22.3+/24.15+/26+) e o usuário local não tinha
acesso ao socket do Docker (`docker ps` retornava "permission denied").
A estrutura de arquivos foi conferida contra o schematic oficial do
`angular-cli` para a versão correspondente. Recomenda-se, na primeira
oportunidade, rodar `npm install` dentro do container de build (o que já
acontece automaticamente no `docker compose up --build`) para validar o
scaffold de ponta a ponta.
