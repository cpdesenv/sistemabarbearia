-- Extensao usada para gerar UUID publico (gen_random_uuid()) nas entidades
-- expostas em URLs e integracoes externas, conforme decisao de identificadores
-- do projeto (BIGINT interno + UUID publico).
CREATE EXTENSION IF NOT EXISTS pgcrypto;
