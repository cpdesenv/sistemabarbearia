-- Reestruturacao do escopo (decisao de custo: tarifacao da API da Meta
-- tornou o canal de WhatsApp inviavel; o agente de IA so existia para
-- conduzir conversas por esse canal, entao sai junto). Remove as tabelas
-- criadas pelas antigas Fases 9 (mensageria/WhatsApp, V25/V26) e 10 (agente
-- de IA, V27). Ordem de DROP respeita as foreign keys: mensagem_envio_outbox
-- e uso_llm referenciam mensagem/conversa; mensagem referencia conversa.
DROP TABLE mensagem_envio_outbox;
DROP TABLE uso_llm;
DROP TABLE mensagem;
DROP TABLE conversa;
DROP TABLE configuracao_ia;
