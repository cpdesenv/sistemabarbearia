-- Fase 11 (etapa 3): produtos mais vendidos e margem, agregados por dia a
-- partir de ComandaItem (tipo PRODUTO). custo_total usa o preco_custo
-- ATUAL do produto no momento da agregacao (nao um snapshot por venda) -
-- decisao deliberada de simplicidade: se o custo do produto mudar depois,
-- relatorios de periodos ja agregados so refletem o novo custo apos um
-- reprocessamento (ver RelatorioAgregacaoService).
CREATE TABLE relatorio_produto_diario (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data                DATE NOT NULL,
    produto_id          BIGINT NOT NULL REFERENCES produto(id),
    produto_nome        VARCHAR(150) NOT NULL,
    quantidade_vendida  INT NOT NULL,
    valor_total         NUMERIC(12,2) NOT NULL,
    custo_total         NUMERIC(12,2) NOT NULL,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_relatorio_produto_diario UNIQUE (data, produto_id)
);

CREATE INDEX idx_relatorio_produto_diario_data ON relatorio_produto_diario (data);
CREATE INDEX idx_relatorio_produto_diario_produto_id ON relatorio_produto_diario (produto_id);

COMMENT ON TABLE relatorio_produto_diario IS 'Fato pre-agregado (Fase 11): quantidade/receita/custo de produtos vendidos por dia, a partir de ComandaItem de comandas FECHADA. Nunca editado por CRUD - so por RelatorioAgregacaoService.';
