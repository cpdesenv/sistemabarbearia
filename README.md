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
