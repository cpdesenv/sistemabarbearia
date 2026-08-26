CREATE TABLE plano_assinatura (
    id                              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico                    UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    nome                            VARCHAR(150) NOT NULL,
    descricao                       TEXT,
    preco_mensal                    NUMERIC(10,2) NOT NULL CHECK (preco_mensal > 0),
    cortes_incluidos_por_ciclo      INT NOT NULL CHECK (cortes_incluidos_por_ciclo > 0),
    percentual_desconto_adicional   NUMERIC(5,2) NOT NULL DEFAULT 0
                                     CHECK (percentual_desconto_adicional BETWEEN 0 AND 100),
    ativo                           BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em                       TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em                   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE plano_assinatura IS 'Plano do Clube Cavalinho: preco mensal, quantidade de cortes inclusos por ciclo e desconto aplicado a servicos/produtos adicionais (fora do saldo). Desativar preserva o historico de assinaturas ja vinculadas a este plano.';

CREATE TABLE plano_assinatura_servico (
    plano_assinatura_id  BIGINT NOT NULL REFERENCES plano_assinatura (id) ON DELETE CASCADE,
    servico_id           BIGINT NOT NULL REFERENCES servico (id),
    PRIMARY KEY (plano_assinatura_id, servico_id)
);

COMMENT ON TABLE plano_assinatura_servico IS 'Servicos que consomem saldo de cortes do plano (um servico fora desta lista e sempre cobrado avulso, mesmo para assinante ativo).';

CREATE TABLE assinatura (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    cliente_id                  BIGINT NOT NULL REFERENCES cliente (id),
    plano_assinatura_id         BIGINT NOT NULL REFERENCES plano_assinatura (id),
    status                      VARCHAR(20) NOT NULL DEFAULT 'ATIVA'
                                 CHECK (status IN ('ATIVA', 'CANCELADA', 'INADIMPLENTE', 'SUSPENSA')),
    saldo_cortes_atual          INT NOT NULL DEFAULT 0 CHECK (saldo_cortes_atual >= 0),
    data_inicio                 DATE NOT NULL,
    data_proxima_renovacao      DATE NOT NULL,
    data_cancelamento           DATE,
    motivo_cancelamento         TEXT,
    criado_em                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em                TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Um cliente so pode ter uma assinatura ATIVA ou INADIMPLENTE por vez; CANCELADA/SUSPENSA
-- ficam de fora do indice, permitindo uma nova assinatura depois de cancelar a anterior.
CREATE UNIQUE INDEX idx_assinatura_cliente_em_curso ON assinatura (cliente_id)
    WHERE (status IN ('ATIVA', 'INADIMPLENTE'));
CREATE INDEX idx_assinatura_status_renovacao ON assinatura (status, data_proxima_renovacao);
CREATE INDEX idx_assinatura_cliente ON assinatura (cliente_id);

COMMENT ON TABLE assinatura IS 'Assinatura de um cliente a um plano do Clube Cavalinho. saldo_cortes_atual e ajustado por UPDATE atomico (mesmo padrao de produto.estoque_atual), garantindo que agendamentos simultaneos nao consumam saldo em duplicidade.';

-- Cada ciclo mensal da assinatura gera uma ContaReceber (cobranca administrativa,
-- recebida manualmente pela recepcao como qualquer outra conta a receber) —
-- nao ha gateway de pagamento no projeto.
ALTER TABLE conta_receber ADD COLUMN assinatura_id BIGINT REFERENCES assinatura (id);
CREATE INDEX idx_conta_receber_assinatura ON conta_receber (assinatura_id);

-- Item de comanda coberto pelo saldo da assinatura (valor zerado nesta comanda,
-- ja pago via mensalidade) — NULL significa item avulso/adicional, cobrado normalmente.
ALTER TABLE comanda_item ADD COLUMN assinatura_id BIGINT REFERENCES assinatura (id);
CREATE INDEX idx_comanda_item_assinatura ON comanda_item (assinatura_id);
