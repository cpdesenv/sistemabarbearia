CREATE TABLE barbearia (
    id                                          BIGINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    nome                                        VARCHAR(150) NOT NULL,
    cnpj                                        VARCHAR(18),
    telefone                                    VARCHAR(20),
    email                                       VARCHAR(180),
    logradouro                                  VARCHAR(200),
    numero                                       VARCHAR(20),
    complemento                                 VARCHAR(100),
    bairro                                      VARCHAR(100),
    cidade                                      VARCHAR(100),
    uf                                          VARCHAR(2),
    cep                                         VARCHAR(9),
    fuso_horario                                VARCHAR(50) NOT NULL DEFAULT 'America/Sao_Paulo',
    antecedencia_minima_agendamento_minutos     INT NOT NULL DEFAULT 0,
    antecedencia_maxima_agendamento_dias        INT NOT NULL DEFAULT 60,
    antecedencia_minima_cancelamento_minutos    INT NOT NULL DEFAULT 120,
    criado_em                                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em                               TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE barbearia IS 'Registro unico de configuracao da barbearia (singleton, id fixo em 1, reforcado pelo CHECK). Nunca criar nem listar via API — apenas ler e editar.';

INSERT INTO barbearia (id, nome, fuso_horario)
VALUES (1, 'Minha Barbearia', 'America/Sao_Paulo');
