CREATE TABLE produto (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico        UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    nome                VARCHAR(255) NOT NULL,
    descricao           TEXT,
    categoria           VARCHAR(100),
    unidade             VARCHAR(20) NOT NULL DEFAULT 'UN',
    preco_venda         NUMERIC(10,2) NOT NULL CHECK (preco_venda >= 0),
    preco_custo         NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (preco_custo >= 0),
    estoque_minimo      INT NOT NULL DEFAULT 0 CHECK (estoque_minimo >= 0),
    estoque_atual       INT NOT NULL DEFAULT 0 CHECK (estoque_atual >= 0),
    ativo               BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE produto IS 'Catalogo de produtos vendidos avulsos ou junto de um servico numa comanda.';
COMMENT ON COLUMN produto.estoque_atual IS 'Saldo em cache, mantido em sincronia com a soma de movimento_estoque via UPDATE atomico (estoque_atual = estoque_atual + delta WHERE estoque_atual + delta >= 0), nunca calculado on-the-fly numa query pesada.';

CREATE TABLE movimento_estoque (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    produto_id          BIGINT NOT NULL REFERENCES produto (id),
    tipo                VARCHAR(20) NOT NULL CHECK (tipo IN ('ENTRADA', 'SAIDA', 'AJUSTE', 'DEVOLUCAO')),
    quantidade          INT NOT NULL CHECK (quantidade <> 0),
    custo_unitario      NUMERIC(10,2),
    motivo              TEXT,
    comanda_id          BIGINT REFERENCES comanda (id),
    usuario_id          BIGINT REFERENCES usuario (id),
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE movimento_estoque IS 'Historico de movimentacoes de estoque. quantidade e um delta com sinal (positivo para ENTRADA/DEVOLUCAO/ajuste-positivo, negativo para SAIDA/ajuste-negativo) — soma-lo da o saldo, mas o saldo de verdade fica cacheado em produto.estoque_atual por performance.';

CREATE INDEX idx_movimento_estoque_produto ON movimento_estoque (produto_id, criado_em DESC);
CREATE INDEX idx_movimento_estoque_comanda ON movimento_estoque (comanda_id);

-- Agora que Produto existe, a comanda_item pode referenciar um produto em vez de
-- (ou alem de) um servico. Ate aqui (migration V18) so existia item de SERVICO.
ALTER TABLE comanda_item
    ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'SERVICO' CHECK (tipo IN ('SERVICO', 'PRODUTO')),
    ADD COLUMN produto_id BIGINT REFERENCES produto (id),
    ALTER COLUMN servico_id DROP NOT NULL;

ALTER TABLE comanda_item
    ADD CONSTRAINT comanda_item_tipo_consistente CHECK (
        (tipo = 'SERVICO' AND servico_id IS NOT NULL AND produto_id IS NULL) OR
        (tipo = 'PRODUTO' AND produto_id IS NOT NULL AND servico_id IS NULL)
    );
