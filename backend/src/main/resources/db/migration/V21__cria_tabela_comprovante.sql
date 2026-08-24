-- Numeracao sequencial do comprovante: uma linha de controle travada com
-- UPDATE ... RETURNING dentro da MESMA transacao do fechamento da comanda
-- (ver ComprovanteService#reservarParaComanda). Isso garante zero buracos:
-- uma SEQUENCE do Postgres nao serviria aqui porque nao e transacional —
-- se o fechamento da comanda desse rollback, o valor da sequence seria
-- perdido para sempre (buraco). Com uma linha comum de tabela, o UPDATE so
-- e commitado se a transacao inteira (fechamento + numeracao) for commitada;
-- se der rollback, o proximo numero reservado sera o mesmo, sem buraco.
CREATE TABLE numeracao_comprovante (
    id              SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    proximo_numero  BIGINT NOT NULL DEFAULT 1 CHECK (proximo_numero > 0)
);

INSERT INTO numeracao_comprovante (id, proximo_numero) VALUES (1, 1);

CREATE TABLE comprovante (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    comanda_id                  BIGINT NOT NULL UNIQUE REFERENCES comanda (id),
    numero                      BIGINT NOT NULL UNIQUE,
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
                                 CHECK (status IN ('PENDENTE', 'DISPONIVEL', 'FALHA')),
    cliente_nome_snapshot       VARCHAR(255) NOT NULL,
    cliente_telefone_snapshot   VARCHAR(30),
    cliente_email_snapshot      VARCHAR(255),
    chave_armazenamento         TEXT,
    gerado_em                   TIMESTAMPTZ,
    ultimo_erro                 TEXT,
    criado_em                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comprovante_status ON comprovante (status);

COMMENT ON TABLE comprovante IS 'Comprovante em PDF gerado ao fechar uma comanda (recibo interno, sem valor fiscal — a Fase 16 substitui o gateway por NFS-e real, sem alterar esta tabela). Numero reservado atomicamente com o fechamento da comanda (ver numeracao_comprovante); a geracao do arquivo (PDF + upload) acontece depois, de forma resiliente, para nunca bloquear o fechamento da comanda por causa do storage.';
COMMENT ON COLUMN comprovante.status IS 'PENDENTE: numero reservado, arquivo ainda nao gerado. DISPONIVEL: PDF gerado e armazenado. FALHA: geracao/upload falhou, disponivel para nova tentativa via /reenviar.';
