CREATE TABLE grade_horaria (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profissional_id     BIGINT NOT NULL REFERENCES profissional (id) ON DELETE CASCADE,
    dia_semana          INTEGER NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    hora_inicio         TIME NOT NULL,
    hora_fim            TIME NOT NULL CHECK (hora_fim > hora_inicio),
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_grade_horaria_profissional ON grade_horaria (profissional_id, dia_semana);

COMMENT ON TABLE grade_horaria IS 'Janelas de atendimento semanais recorrentes por profissional (1=segunda...7=domingo, ISO-8601). Varias linhas no mesmo dia permitem multiplos turnos (ex: manha e tarde).';
