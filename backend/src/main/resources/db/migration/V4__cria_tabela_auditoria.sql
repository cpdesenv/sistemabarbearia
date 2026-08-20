CREATE TABLE auditoria (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id      BIGINT REFERENCES usuario (id),
    operacao        VARCHAR(60) NOT NULL,
    entidade        VARCHAR(60),
    entidade_id     BIGINT,
    descricao       TEXT,
    ip              VARCHAR(45),
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auditoria_entidade ON auditoria (entidade, entidade_id);
CREATE INDEX idx_auditoria_usuario_id ON auditoria (usuario_id);
CREATE INDEX idx_auditoria_criado_em ON auditoria (criado_em);

COMMENT ON TABLE auditoria IS 'Trilha de auditoria generica, reaproveitada por todas as fases seguintes.';
