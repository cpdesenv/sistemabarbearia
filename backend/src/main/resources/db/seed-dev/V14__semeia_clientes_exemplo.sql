-- Dados de exemplo para desenvolvimento local. So aplicado no perfil dev
-- (ver spring.flyway.locations em application-dev.yml) — nunca em test/prod.

INSERT INTO cliente (nome, telefone, email, cpf, opt_in_whatsapp, origem_cadastro, consentimento_lgpd, consentimento_lgpd_em) VALUES
    ('Marcos Vinicius Prado', '+5519999110001', 'marcos.prado@exemplo.com', NULL, TRUE, 'PAINEL', TRUE, now()),
    ('Felipe Augusto Ramos', '+5519999110002', NULL, NULL, TRUE, 'WHATSAPP', FALSE, NULL),
    ('Gustavo Henrique Melo', '+5519999110003', 'gustavo.melo@exemplo.com', NULL, FALSE, 'PORTAL', TRUE, now());
