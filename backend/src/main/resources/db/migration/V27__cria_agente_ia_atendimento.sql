-- Fase 10: agente de IA de atendimento e agendamento via tool-calling
-- (Anthropic Claude). modo_atendimento/turnos_ia/contexto_expira_em
-- controlam quando a IA responde uma conversa (ver AgenteAtendimentoService)
-- versus quando ela foi escalada para atendimento humano — por guardrail
-- (limite de turnos, reclamacao, assunto fora do escopo) ou pelo botao
-- "assumir conversa" no painel.
ALTER TABLE conversa ADD COLUMN modo_atendimento VARCHAR(10) NOT NULL DEFAULT 'IA'
    CHECK (modo_atendimento IN ('IA', 'HUMANO'));
ALTER TABLE conversa ADD COLUMN turnos_ia INT NOT NULL DEFAULT 0;
ALTER TABLE conversa ADD COLUMN contexto_expira_em TIMESTAMPTZ;
ALTER TABLE conversa ADD COLUMN motivo_escalonamento TEXT;

CREATE INDEX idx_conversa_modo_atendimento ON conversa (modo_atendimento);

-- Registro unico (singleton, id fixo em 1) da configuracao do agente de IA:
-- kill switch e teto de custo mensal (guardrails obrigatorios do PRD, Fase
-- 10). Editavel pelo painel — ver IaConfiguracaoController.
CREATE TABLE configuracao_ia (
    id                          BIGINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    ativo                       BOOLEAN NOT NULL DEFAULT TRUE,
    limite_turnos               INT NOT NULL DEFAULT 25,
    teto_custo_mensal_centavos  BIGINT NOT NULL DEFAULT 10000,
    criado_em                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em               TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE configuracao_ia IS 'Registro unico (singleton, id fixo em 1) da configuracao do agente de IA. ativo=false e o kill switch: desliga a IA e joga toda conversa em modo HUMANO imediatamente (ver AgenteAtendimentoService).';

INSERT INTO configuracao_ia (id) VALUES (1);

-- Um registro por chamada ao LLM (nao por conversa) — permite somar custo por
-- conversa (exibido no painel) e por mes corrente (para aplicar o teto de
-- configuracao_ia.teto_custo_mensal_centavos).
CREATE TABLE uso_llm (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversa_id       BIGINT NOT NULL REFERENCES conversa (id),
    modelo            VARCHAR(50) NOT NULL,
    tokens_entrada    INT NOT NULL,
    tokens_saida      INT NOT NULL,
    custo_centavos    NUMERIC(12, 4) NOT NULL,
    criado_em         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_uso_llm_criado_em ON uso_llm (criado_em);
CREATE INDEX idx_uso_llm_conversa ON uso_llm (conversa_id);

COMMENT ON TABLE uso_llm IS 'Uma linha por chamada ao AiAgentGateway, para tracking de tokens/custo por conversa e aplicacao do teto de custo mensal (configuracao_ia.teto_custo_mensal_centavos).';
