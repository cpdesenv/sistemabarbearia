-- Fase 11 (etapa 2): agregacao diaria de agenda por profissional, povoada
-- pelo mesmo job/reprocessamento de RelatorioAgregacaoService. Uma linha
-- por (data, profissional ativo) mesmo em dias sem nenhum agendamento -
-- necessario para o denominador de ocupacao (minutos_capacidade) nao ficar
-- subestimado ao somar varios dias.
CREATE TABLE relatorio_agenda_diario (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data                        DATE NOT NULL,
    profissional_id             BIGINT NOT NULL REFERENCES profissional(id),
    profissional_nome           VARCHAR(150) NOT NULL,
    quantidade_finalizados      INT NOT NULL,
    quantidade_cancelados       INT NOT NULL,
    quantidade_nao_compareceu   INT NOT NULL,
    minutos_capacidade          INT NOT NULL,
    minutos_ocupados            INT NOT NULL,
    criado_em                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_relatorio_agenda_diario UNIQUE (data, profissional_id)
);

CREATE INDEX idx_relatorio_agenda_diario_data ON relatorio_agenda_diario (data);
CREATE INDEX idx_relatorio_agenda_diario_profissional_id ON relatorio_agenda_diario (profissional_id);

COMMENT ON TABLE relatorio_agenda_diario IS 'Fato pre-agregado (Fase 11): cancelamentos, faltas e ocupacao por dia x profissional. Nunca editado por CRUD - so por RelatorioAgregacaoService.';
