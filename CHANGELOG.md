# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/).

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
