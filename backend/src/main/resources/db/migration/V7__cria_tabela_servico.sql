CREATE TABLE servico (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico        UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    nome                VARCHAR(150) NOT NULL,
    descricao           TEXT,
    categoria           VARCHAR(80),
    preco               NUMERIC(10,2) NOT NULL CHECK (preco > 0),
    duracao_minutos     INT NOT NULL CHECK (duracao_minutos > 0),
    ativo               BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_servico_ativo ON servico (ativo);
CREATE INDEX idx_servico_categoria ON servico (categoria);

COMMENT ON TABLE servico IS 'Servicos oferecidos pela barbearia (corte, barba, combos etc).';
