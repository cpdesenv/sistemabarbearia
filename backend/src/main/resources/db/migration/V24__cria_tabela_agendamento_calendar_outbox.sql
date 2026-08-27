-- Outbox transacional (Fase 8): a intencao de sincronizar um agendamento com
-- o Google Calendar e gravada na mesma transacao que confirma/remarca/cancela
-- o agendamento (ver AgendamentoService), e um worker assincrono (@Scheduled,
-- ver CalendarOutboxWorker) processa essas linhas depois, com retentativa e
-- backoff exponencial — assim uma falha na chamada ao Google nunca derruba a
-- operacao de agenda em si.
CREATE TABLE agendamento_calendar_outbox (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agendamento_id        BIGINT NOT NULL REFERENCES agendamento (id),
    tipo_operacao         VARCHAR(20) NOT NULL CHECK (tipo_operacao IN ('CRIAR', 'ATUALIZAR', 'REMOVER')),
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
                           CHECK (status IN ('PENDENTE', 'CONCLUIDO', 'FALHA_PERMANENTE')),
    tentativas            INT NOT NULL DEFAULT 0,
    proxima_tentativa_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_erro           TEXT,
    criado_em             TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- No maximo uma linha PENDENTE por agendamento: uma nova intencao de sync
-- (ex.: remarcar de novo antes do worker processar a primeira tentativa)
-- atualiza a linha existente em vez de empilhar duplicatas fora de ordem.
CREATE UNIQUE INDEX idx_outbox_agendamento_pendente ON agendamento_calendar_outbox (agendamento_id)
    WHERE (status = 'PENDENTE');
CREATE INDEX idx_outbox_status_proxima_tentativa ON agendamento_calendar_outbox (status, proxima_tentativa_em);

COMMENT ON TABLE agendamento_calendar_outbox IS 'Fila de sincronizacao com o Google Calendar. status=FALHA_PERMANENTE so ocorre apos exceder o numero maximo de tentativas (ver CalendarOutboxWorker); ate la a linha continua PENDENTE e e retentada com backoff, aparecendo no painel como agendamento fora de sincronia. O botao "Ressincronizar agenda" volta qualquer linha nao concluida para PENDENTE com tentativas=0.';
