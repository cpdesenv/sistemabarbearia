-- Correcao de dados de exemplo (perfil dev). O seed original (V12) nao era
-- idempotente: ao rodar num banco que ja tinha um servico "Corte Masculino"
-- criado manualmente antes da migration existir, ele inseriu um segundo
-- registro com o mesmo nome em vez de reaproveitar o existente, e os
-- profissionais criados manualmente nesse mesmo periodo (Carlos Barbeiro,
-- Victor Perdomo, Grade Teste) ficaram sem nenhum vinculo em
-- profissional_servico. Efeito visivel: "Corte Masculino" duplicado no
-- formulario de agendamento, e erro "profissional nao realiza o servico"
-- para qualquer combinacao envolvendo esses profissionais.

-- 1) Deduplica "Corte Masculino": mantem o registro mais antigo (id menor) e
--    remove o duplicado, repontando/removendo vinculos antes de apagar.
DO $$
DECLARE
    manter_id BIGINT;
    remover_id BIGINT;
BEGIN
    SELECT id INTO manter_id FROM servico WHERE nome = 'Corte Masculino' ORDER BY id LIMIT 1;
    SELECT id INTO remover_id FROM servico WHERE nome = 'Corte Masculino' AND id <> manter_id LIMIT 1;

    IF remover_id IS NOT NULL THEN
        DELETE FROM profissional_servico WHERE servico_id = remover_id;
        DELETE FROM agendamento_servico WHERE servico_id = remover_id; -- defensivo; nao deve haver linhas
        DELETE FROM servico WHERE id = remover_id;
    END IF;
END $$;

-- 2) Vincula os profissionais de teste criados manualmente (Fases 2-4) a
--    servicos coerentes. Guardado por nome: em um banco novo, onde esses
--    profissionais nunca existiram, os blocos abaixo nao inserem nada.
INSERT INTO profissional_servico (profissional_id, servico_id, comissao_percentual)
SELECT p.id, s.id, NULL
FROM profissional p
CROSS JOIN servico s
WHERE p.nome = 'Carlos Barbeiro'
  AND s.nome IN ('Corte Masculino', 'Barba Completa', 'Sombrancelha', 'limpeza de pele')
  AND NOT EXISTS (
      SELECT 1 FROM profissional_servico ps
      WHERE ps.profissional_id = p.id AND ps.servico_id = s.id
  );

INSERT INTO profissional_servico (profissional_id, servico_id, comissao_percentual)
SELECT p.id, s.id, NULL
FROM profissional p
CROSS JOIN servico s
WHERE p.nome = 'Victor Perdomo'
  AND s.nome IN ('Barba Completa', 'Sombrancelha', 'limpeza de pele')
  AND NOT EXISTS (
      SELECT 1 FROM profissional_servico ps
      WHERE ps.profissional_id = p.id AND ps.servico_id = s.id
  );

INSERT INTO profissional_servico (profissional_id, servico_id, comissao_percentual)
SELECT p.id, s.id, NULL
FROM profissional p
CROSS JOIN servico s
WHERE p.nome = 'Grade Teste'
  AND s.nome = 'Teste'
  AND NOT EXISTS (
      SELECT 1 FROM profissional_servico ps
      WHERE ps.profissional_id = p.id AND ps.servico_id = s.id
  );
