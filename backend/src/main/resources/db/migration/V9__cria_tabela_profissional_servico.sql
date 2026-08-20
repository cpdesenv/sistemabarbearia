CREATE TABLE profissional_servico (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profissional_id         BIGINT NOT NULL REFERENCES profissional (id) ON DELETE CASCADE,
    servico_id              BIGINT NOT NULL REFERENCES servico (id) ON DELETE CASCADE,
    comissao_percentual     NUMERIC(5,2) CHECK (comissao_percentual >= 0 AND comissao_percentual <= 100),
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (profissional_id, servico_id)
);

CREATE INDEX idx_profissional_servico_profissional ON profissional_servico (profissional_id);
CREATE INDEX idx_profissional_servico_servico ON profissional_servico (servico_id);

COMMENT ON TABLE profissional_servico IS 'Vinculo N:N entre profissional e servico. comissao_percentual nula significa que vale a comissao padrao do profissional.';
