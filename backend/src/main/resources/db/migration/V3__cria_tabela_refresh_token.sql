CREATE TABLE refresh_token (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuario (id),
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    expira_em       TIMESTAMPTZ NOT NULL,
    revogado_em     TIMESTAMPTZ,
    criado_por_ip   VARCHAR(45),
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_token_usuario_id ON refresh_token (usuario_id);

COMMENT ON TABLE refresh_token IS 'Refresh tokens opacos (hash SHA-256), permitem revogacao no logout e rotacao a cada uso.';
