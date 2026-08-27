-- Outbox transacional para ENVIO de mensagem (mesmo padrao da Fase 8,
-- agendamento_calendar_outbox): a mensagem SAIDA e a linha de outbox nascem
-- na mesma transacao; o worker chama o WhatsAppGateway depois, com
-- retentativa e backoff — uma falha simulada de envio nunca perde a
-- mensagem, so atrasa. O RECEBIMENTO de mensagem nao precisa de outbox: e
-- gravacao idempotente (unique em mensagem.wa_message_id) processada de
-- forma assincrona (virtual thread), sem chamada de rede envolvida.
CREATE TABLE mensagem_envio_outbox (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mensagem_id           BIGINT NOT NULL UNIQUE REFERENCES mensagem (id),
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
                           CHECK (status IN ('PENDENTE', 'CONCLUIDO', 'FALHA_PERMANENTE')),
    tentativas            INT NOT NULL DEFAULT 0,
    proxima_tentativa_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_erro           TEXT,
    criado_em             TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_envio_outbox_status_proxima_tentativa ON mensagem_envio_outbox (status, proxima_tentativa_em);

COMMENT ON TABLE mensagem_envio_outbox IS 'Fila de envio de mensagem de WhatsApp (ver MensagemEnvioOutboxWorker). FALHA_PERMANENTE so ocorre apos exceder o numero maximo de tentativas.';
