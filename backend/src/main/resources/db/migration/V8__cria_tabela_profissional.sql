CREATE TABLE profissional (
    id                              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico                    UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    nome                            VARCHAR(150) NOT NULL,
    email                           VARCHAR(180),
    telefone                        VARCHAR(20),
    cor_agenda                      VARCHAR(7) NOT NULL DEFAULT '#3F51B5',
    comissao_percentual_padrao      NUMERIC(5,2) NOT NULL DEFAULT 0
                                     CHECK (comissao_percentual_padrao >= 0 AND comissao_percentual_padrao <= 100),
    ativo                           BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em                       TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em                   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_profissional_ativo ON profissional (ativo);

COMMENT ON TABLE profissional IS 'Barbeiros/profissionais que atendem na barbearia.';
