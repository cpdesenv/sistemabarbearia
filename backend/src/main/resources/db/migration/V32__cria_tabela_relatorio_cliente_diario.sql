-- Fase 11 (etapa 2): novos vs. recorrentes por dia (todo o negocio, sem
-- dimensao de profissional/servico - e' uma propriedade do cliente, nao do
-- atendimento). "Novo" = o cliente nao tinha nenhuma comanda FECHADA antes
-- do inicio deste dia (ver RelatorioAgregacaoService).
CREATE TABLE relatorio_cliente_diario (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data                    DATE NOT NULL UNIQUE,
    clientes_novos          INT NOT NULL,
    clientes_recorrentes    INT NOT NULL,
    atendimentos_totais     INT NOT NULL,
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_relatorio_cliente_diario_data ON relatorio_cliente_diario (data);

COMMENT ON TABLE relatorio_cliente_diario IS 'Fato pre-agregado (Fase 11): clientes novos vs. recorrentes por dia. Nunca editado por CRUD - so por RelatorioAgregacaoService.';
