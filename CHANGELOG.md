# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/).

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
