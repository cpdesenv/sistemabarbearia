-- Registro unico (singleton, id fixo em 1) da integracao com o Google Calendar
-- da barbearia (Fase 8). refresh_token_criptografado e o unico segredo
-- persistido, sempre criptografado com AES-256-GCM (ver CriptografiaService)
-- — o access token nunca e salvo, e sempre renovado a partir do refresh token
-- pela biblioteca oficial do Google (google-auth-library-oauth2-http).
CREATE TABLE integracao_google_calendar (
    id                              BIGINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    modo                            VARCHAR(20) NOT NULL DEFAULT 'CALENDARIO_UNICO'
                                     CHECK (modo IN ('CALENDARIO_UNICO', 'POR_PROFISSIONAL')),
    calendario_id_unico             VARCHAR(255),
    refresh_token_criptografado     TEXT,
    conectado_em                    TIMESTAMPTZ,
    conectado_por_usuario_id        BIGINT REFERENCES usuario (id),
    state_pendente                  VARCHAR(100),
    state_expira_em                 TIMESTAMPTZ,
    state_iniciado_por_usuario_id   BIGINT REFERENCES usuario (id),
    ultimo_erro                     TEXT,
    criado_em                       TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em                   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE integracao_google_calendar IS 'Registro unico de integracao com o Google Calendar (singleton, id fixo em 1, reforcado pelo CHECK). state_pendente/state_expira_em guardam o token CSRF de uma autorizacao OAuth2 em andamento, de uso unico e expiracao curta (5 minutos) — ver IntegracaoGoogleCalendarService.';

INSERT INTO integracao_google_calendar (id) VALUES (1);

-- Calendario proprio do profissional, usado apenas quando o modo da integracao
-- e POR_PROFISSIONAL (ver integracao_google_calendar.modo).
ALTER TABLE profissional ADD COLUMN google_calendar_id VARCHAR(255);

-- Guarda em qual calendario (unico ou do profissional, resolvido no momento da
-- criacao do evento) o evento deste agendamento foi criado, para que
-- atualizacao/remocao usem sempre o mesmo calendario, mesmo que o profissional
-- do agendamento mude depois numa remarcacao.
ALTER TABLE agendamento ADD COLUMN google_calendar_id VARCHAR(255);
