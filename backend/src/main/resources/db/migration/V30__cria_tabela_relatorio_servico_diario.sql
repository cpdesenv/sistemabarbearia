-- Fase 11 (etapa 1): tabela de agregacao diaria de servicos, povoada pelo
-- job noturno RelatorioAgregacaoScheduler (ou pelo endpoint de
-- reprocessamento, para backfill do historico e correcoes apos estorno
-- tardio). Nome/forma de pagamento sao snapshots (mesmo padrao de
-- comanda_item.descricao) para o relatorio nunca precisar de join contra
-- profissional/servico em tempo de consulta.
CREATE TABLE relatorio_servico_diario (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data                DATE NOT NULL,
    profissional_id     BIGINT NOT NULL REFERENCES profissional(id),
    profissional_nome   VARCHAR(150) NOT NULL,
    servico_id          BIGINT NOT NULL REFERENCES servico(id),
    servico_nome        VARCHAR(150) NOT NULL,
    forma_pagamento     VARCHAR(20) NOT NULL,
    quantidade          INT NOT NULL,
    valor_total         NUMERIC(12,2) NOT NULL,
    comissao_total      NUMERIC(12,2) NOT NULL,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_relatorio_servico_diario UNIQUE (data, profissional_id, servico_id, forma_pagamento)
);

CREATE INDEX idx_relatorio_servico_diario_data ON relatorio_servico_diario (data);
CREATE INDEX idx_relatorio_servico_diario_profissional_id ON relatorio_servico_diario (profissional_id);
CREATE INDEX idx_relatorio_servico_diario_servico_id ON relatorio_servico_diario (servico_id);

COMMENT ON TABLE relatorio_servico_diario IS 'Fato pre-agregado (Fase 11): faturamento/comissao/quantidade por dia x profissional x servico x forma de pagamento, a partir de ComandaItem de comandas FECHADA. Nunca editado por CRUD - so por RelatorioAgregacaoService.';
