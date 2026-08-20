# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/).

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
