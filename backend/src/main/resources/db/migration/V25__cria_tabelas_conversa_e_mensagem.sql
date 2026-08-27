-- Conversa por WhatsApp (Fase 9): sempre vinculada a um cliente. Cliente
-- desconhecido pelo telefone nasce como rascunho (origem_cadastro=WHATSAPP,
-- ver ClienteRepository/OrigemCadastro) no momento da primeira mensagem.
CREATE TABLE conversa (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico        UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    cliente_id          BIGINT NOT NULL REFERENCES cliente (id),
    telefone_e164       VARCHAR(20) NOT NULL UNIQUE,
    ultima_mensagem_em  TIMESTAMPTZ,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversa_cliente ON conversa (cliente_id);

COMMENT ON TABLE conversa IS 'Uma conversa por numero de telefone (telefone_e164 e a identidade da conversa, como no proprio WhatsApp). cliente_id sempre aponta para um cliente existente ou recem-criado como rascunho.';

-- wa_message_id e o id que o provedor (ou o mock) atribui a mensagem — unico
-- quando presente, e o que garante a idempotencia do webhook: reenviar o
-- mesmo payload tenta inserir a mesma mensagem_id de novo, o banco rejeita
-- por violacao de unicidade, e o service trata isso como no-op (ver
-- MensageriaInboundService).
CREATE TABLE mensagem (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico        UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    conversa_id         BIGINT NOT NULL REFERENCES conversa (id),
    direcao             VARCHAR(10) NOT NULL CHECK (direcao IN ('ENTRADA', 'SAIDA')),
    tipo                VARCHAR(20) NOT NULL DEFAULT 'TEXTO'
                        CHECK (tipo IN ('TEXTO', 'TEMPLATE', 'INTERATIVO', 'DOCUMENTO')),
    conteudo            TEXT,
    wa_message_id       VARCHAR(255) UNIQUE,
    status              VARCHAR(20) NOT NULL
                        CHECK (status IN ('RECEBIDA', 'PENDENTE', 'ENVIADA', 'ENTREGUE', 'LIDA', 'FALHA')),
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mensagem_conversa ON mensagem (conversa_id, criado_em);

COMMENT ON TABLE mensagem IS 'Mensagem de uma conversa de WhatsApp, ENTRADA (recebida do cliente) ou SAIDA (enviada pela barbearia, incluindo o eco automatico). Mensagens SAIDA nascem com status PENDENTE e sao processadas por MensagemEnvioOutboxWorker; ENVIADA/ENTREGUE/LIDA avancam sozinhas por simulacao de tempo (StatusMensagemSimuladorWorker), so no gateway mock.';
