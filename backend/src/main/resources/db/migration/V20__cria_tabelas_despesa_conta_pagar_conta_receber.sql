CREATE TABLE despesa (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico        UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    data                DATE NOT NULL,
    categoria           VARCHAR(100),
    valor               NUMERIC(10,2) NOT NULL CHECK (valor >= 0),
    descricao           TEXT,
    comprovante_url     TEXT,
    usuario_id          BIGINT REFERENCES usuario (id),
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_despesa_data ON despesa (data);

COMMENT ON TABLE despesa IS 'Lancamento de despesa avulsa (aluguel, contas, compras nao ligadas a produto revendido). Reduz o caixa em maos no calculo de fluxo de caixa.';

CREATE TABLE conta_pagar (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico        UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    descricao           VARCHAR(255) NOT NULL,
    valor               NUMERIC(10,2) NOT NULL CHECK (valor >= 0),
    data_vencimento     DATE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE', 'PAGA', 'CANCELADA')),
    data_pagamento      DATE,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conta_pagar_status_vencimento ON conta_pagar (status, data_vencimento);

COMMENT ON TABLE conta_pagar IS 'Conta a pagar (fornecedor/compromisso futuro). Entra no fluxo de caixa apenas quando PENDENTE e vencida (data_vencimento no passado).';

CREATE TABLE conta_receber (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico        UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    cliente_id          BIGINT NOT NULL REFERENCES cliente (id),
    descricao           VARCHAR(255),
    valor               NUMERIC(10,2) NOT NULL CHECK (valor >= 0),
    data_vencimento     DATE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE', 'RECEBIDA', 'CANCELADA')),
    data_recebimento    DATE,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conta_receber_status_vencimento ON conta_receber (status, data_vencimento);
CREATE INDEX idx_conta_receber_cliente ON conta_receber (cliente_id);

COMMENT ON TABLE conta_receber IS 'Debito de cliente (ex.: servico fiado). Todo valor PENDENTE conta como "esperado" no fluxo de caixa, independente da data de vencimento.';
