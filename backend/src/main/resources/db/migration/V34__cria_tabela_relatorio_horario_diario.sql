-- Fase 11 (etapa 3): base do heatmap de horarios de maior movimento.
-- Granularidade por (data, hora) - dia da semana e' derivado de "data" na
-- leitura (RelatorioHeatmapService), nao armazenado, para o mesmo relatorio
-- servir qualquer intervalo de datas. So existe linha para horas com pelo
-- menos um agendamento FINALIZADO no dia (tabela esparsa).
CREATE TABLE relatorio_horario_diario (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data                    DATE NOT NULL,
    hora                    INT NOT NULL CHECK (hora BETWEEN 0 AND 23),
    quantidade_finalizados  INT NOT NULL,
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_relatorio_horario_diario UNIQUE (data, hora)
);

CREATE INDEX idx_relatorio_horario_diario_data ON relatorio_horario_diario (data);

COMMENT ON TABLE relatorio_horario_diario IS 'Fato pre-agregado (Fase 11): quantidade de agendamentos FINALIZADO por dia x hora, base do heatmap de horarios de maior movimento. Nunca editado por CRUD - so por RelatorioAgregacaoService.';
