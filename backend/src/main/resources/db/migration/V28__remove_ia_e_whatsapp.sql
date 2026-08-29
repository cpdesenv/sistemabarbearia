-- O cliente decidiu nao utilizar o canal de mensageria via WhatsApp nem o
-- agente de atendimento por IA (Fases 9, 10 e 11). Esta migration remove
-- o schema criado por V25/V26/V27, sem reescrever o historico do Flyway.
DROP TABLE IF EXISTS uso_llm;
DROP TABLE IF EXISTS configuracao_ia;
DROP TABLE IF EXISTS mensagem_envio_outbox;
DROP TABLE IF EXISTS mensagem;
DROP TABLE IF EXISTS conversa;
