CREATE TABLE comanda (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    agendamento_id              BIGINT NOT NULL REFERENCES agendamento (id),
    status                      VARCHAR(20) NOT NULL DEFAULT 'ABERTA'
                                 CHECK (status IN ('ABERTA', 'FECHADA', 'ESTORNADA')),
    desconto_valor              NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (desconto_valor >= 0),
    desconto_motivo             TEXT,
    forma_pagamento             VARCHAR(20)
                                 CHECK (forma_pagamento IN ('DINHEIRO', 'CARTAO_DEBITO', 'CARTAO_CREDITO', 'PIX', 'OUTRO')),
    subtotal                    NUMERIC(10,2) NOT NULL DEFAULT 0,
    valor_total                 NUMERIC(10,2) NOT NULL DEFAULT 0,
    fechada_em                  TIMESTAMPTZ,
    fechada_por_usuario_id      BIGINT REFERENCES usuario (id),
    estornada_em                TIMESTAMPTZ,
    estornada_por_usuario_id    BIGINT REFERENCES usuario (id),
    motivo_estorno              TEXT,
    criado_em                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em                TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- No maximo uma comanda ABERTA por agendamento por vez (concorrencia), mas
-- o historico de comandas FECHADA/ESTORNADA para o mesmo agendamento fica
-- preservado (permite reabrir uma nova comanda apos um estorno, para
-- corrigir um erro, sem apagar o rastro da tentativa anterior).
CREATE UNIQUE INDEX idx_comanda_agendamento_aberta ON comanda (agendamento_id) WHERE (status = 'ABERTA');
CREATE INDEX idx_comanda_agendamento ON comanda (agendamento_id);
CREATE INDEX idx_comanda_status_fechada_em ON comanda (status, fechada_em);

COMMENT ON TABLE comanda IS 'Comanda de atendimento: itens (servicos/produtos), desconto, forma de pagamento e fechamento. Sempre vinculada a um agendamento. Comanda FECHADA e imutavel; correcao e feita por estorno (ESTORNADA) seguido de nova comanda para o mesmo agendamento.';

CREATE TABLE comanda_item (
    id                            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico                  UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    comanda_id                    BIGINT NOT NULL REFERENCES comanda (id) ON DELETE CASCADE,
    servico_id                    BIGINT NOT NULL REFERENCES servico (id),
    descricao                     VARCHAR(255) NOT NULL,
    quantidade                    INT NOT NULL DEFAULT 1 CHECK (quantidade > 0),
    valor_unitario                NUMERIC(10,2) NOT NULL CHECK (valor_unitario >= 0),
    valor_bruto                   NUMERIC(10,2) NOT NULL CHECK (valor_bruto >= 0),
    valor_desconto_rateado        NUMERIC(10,2) NOT NULL DEFAULT 0,
    valor_liquido                 NUMERIC(10,2) NOT NULL,
    comissao_percentual_aplicado  NUMERIC(5,2),
    comissao_valor                NUMERIC(10,2),
    criado_em                     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comanda_item_comanda ON comanda_item (comanda_id);

COMMENT ON TABLE comanda_item IS 'Itens de uma comanda. Nesta sub-entrega (5A) somente servicos; produtos entram na sub-entrega 5B via ALTER TABLE (servico_id passa a ser opcional, adiciona produto_id e tipo).';
