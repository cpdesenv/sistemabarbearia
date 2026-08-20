CREATE TABLE bloqueio (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico        UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    profissional_id     BIGINT REFERENCES profissional (id) ON DELETE CASCADE,
    inicio              TIMESTAMPTZ NOT NULL,
    fim                 TIMESTAMPTZ NOT NULL CHECK (fim > inicio),
    motivo              VARCHAR(200) NOT NULL,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_bloqueio_profissional ON bloqueio (profissional_id);
CREATE INDEX idx_bloqueio_periodo ON bloqueio (inicio, fim);

COMMENT ON TABLE bloqueio IS 'Bloqueios pontuais de agenda (ferias, atestado, feriado). profissional_id nulo = bloqueio global (fecha a barbearia inteira no periodo). Independente da grade_horaria: um bloqueio pode se sobrepor a uma janela de atendimento normal e prevalece sobre ela.';
