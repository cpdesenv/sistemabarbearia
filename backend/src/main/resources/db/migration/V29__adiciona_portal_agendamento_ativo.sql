-- Fase 9: link de autoagendamento publico. Flag para o painel poder
-- desativar o portal globalmente sem precisar de deploy.
ALTER TABLE barbearia ADD COLUMN portal_agendamento_ativo BOOLEAN NOT NULL DEFAULT true;
