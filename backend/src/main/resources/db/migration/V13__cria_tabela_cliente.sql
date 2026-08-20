CREATE TABLE cliente (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    nome                    VARCHAR(150) NOT NULL,
    telefone                VARCHAR(20) UNIQUE,
    whatsapp                VARCHAR(20),
    cpf                     VARCHAR(11) UNIQUE,
    email                   VARCHAR(180),
    logradouro              VARCHAR(200),
    numero                  VARCHAR(20),
    complemento             VARCHAR(100),
    bairro                  VARCHAR(100),
    cidade                  VARCHAR(100),
    uf                      VARCHAR(2),
    cep                     VARCHAR(9),
    data_nascimento         DATE,
    observacoes             TEXT,
    opt_in_whatsapp         BOOLEAN NOT NULL DEFAULT TRUE,
    origem_cadastro         VARCHAR(20) NOT NULL DEFAULT 'PAINEL',
    consentimento_lgpd      BOOLEAN NOT NULL DEFAULT FALSE,
    consentimento_lgpd_em   TIMESTAMPTZ,
    anonimizado_em          TIMESTAMPTZ,
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_cliente_nome ON cliente (lower(nome));
CREATE INDEX idx_cliente_cpf ON cliente (cpf);

COMMENT ON TABLE cliente IS 'Clientes da barbearia. Telefone e CPF sao unicos (o telefone e a chave natural usada pela mensageria para identificar o cliente). Anonimizado_em marca clientes que passaram por exclusao LGPD — a linha e mantida para integridade referencial futura (agendamentos, comandas, notas fiscais), mas os campos pessoais sao zerados.';
