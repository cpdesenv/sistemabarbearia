CREATE TABLE usuario (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid_publico        UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    nome                VARCHAR(150) NOT NULL,
    email               VARCHAR(180) NOT NULL UNIQUE,
    senha_hash          VARCHAR(100) NOT NULL,
    perfil              VARCHAR(20) NOT NULL CHECK (perfil IN ('ADMIN', 'GERENTE', 'BARBEIRO', 'RECEPCAO')),
    ativo               BOOLEAN NOT NULL DEFAULT TRUE,
    ultimo_acesso_em    TIMESTAMPTZ,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE usuario IS 'Usuarios com acesso ao painel administrativo (nao confundir com cliente).';
