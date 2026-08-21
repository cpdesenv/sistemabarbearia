-- btree_gist e' necessaria para a constraint de exclusao abaixo poder
-- comparar profissional_id (igualdade) junto com o range de horario
-- (sobreposicao) num unico indice GiST.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE agendamento (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    cliente_id              BIGINT NOT NULL REFERENCES cliente (id),
    profissional_id         BIGINT NOT NULL REFERENCES profissional (id),
    inicio                  TIMESTAMPTZ NOT NULL,
    fim                     TIMESTAMPTZ NOT NULL CHECK (fim > inicio),
    valor_total             NUMERIC(10,2) NOT NULL CHECK (valor_total >= 0),
    status                  VARCHAR(20) NOT NULL DEFAULT 'AGENDADO'
                             CHECK (status IN ('AGENDADO', 'CONFIRMADO', 'EM_ATENDIMENTO', 'FINALIZADO',
                                                'CANCELADO', 'NAO_COMPARECEU')),
    origem                  VARCHAR(20) NOT NULL
                             CHECK (origem IN ('WHATSAPP', 'PORTAL', 'PAINEL', 'MANUAL')),
    google_event_id         VARCHAR(255),
    observacao              TEXT,
    motivo_cancelamento     TEXT,
    usuario_criador_id      BIGINT REFERENCES usuario (id),
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- A garantia real contra corrida de concorrencia: impede dois
    -- agendamentos que ocupem (bloqueiem a agenda) do mesmo profissional
    -- com horarios sobrepostos. CANCELADO/NAO_COMPARECEU ficam de fora
    -- porque nao ocupam mais a agenda.
    CONSTRAINT agendamento_sem_sobreposicao EXCLUDE USING gist (
        profissional_id WITH =,
        tstzrange(inicio, fim) WITH &&
    ) WHERE (status NOT IN ('CANCELADO', 'NAO_COMPARECEU'))
);

CREATE INDEX idx_agendamento_profissional_periodo ON agendamento (profissional_id, inicio);
CREATE INDEX idx_agendamento_cliente ON agendamento (cliente_id);
CREATE INDEX idx_agendamento_status ON agendamento (status);

COMMENT ON TABLE agendamento IS 'Agendamentos da barbearia. Nunca deletado fisicamente — cancelamento e soft delete (status=CANCELADO + motivo_cancelamento).';

CREATE TABLE agendamento_servico (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agendamento_id      BIGINT NOT NULL REFERENCES agendamento (id) ON DELETE CASCADE,
    servico_id          BIGINT NOT NULL REFERENCES servico (id),
    duracao_minutos     INT NOT NULL CHECK (duracao_minutos > 0),
    preco               NUMERIC(10,2) NOT NULL CHECK (preco >= 0),
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_agendamento_servico_agendamento ON agendamento_servico (agendamento_id);

COMMENT ON TABLE agendamento_servico IS 'Servicos incluidos em cada agendamento (N:N). duracao_minutos e preco sao um snapshot do servico no momento do agendamento — mudar o preco/duracao de um servico depois nao deve alterar agendamentos ja criados.';
