-- Fase 9: link de autoagendamento publico. Kill switch simples: desligado, o
-- painel para de divulgar o link e o backend responde indisponivel em
-- /api/autoagendamento/**.
ALTER TABLE barbearia ADD COLUMN portal_autoagendamento_ativo BOOLEAN NOT NULL DEFAULT TRUE;
