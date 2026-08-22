-- Dados de exemplo para desenvolvimento local. So aplicado no perfil dev
-- (ver spring.flyway.locations em application-dev.yml) — nunca em test/prod.

UPDATE barbearia SET
    nome = 'Cortes Cavalinho',
    cnpj = '12.345.678/0001-90',
    telefone = '(19) 99999-0000',
    email = 'contato@cortescavalinho.com.br',
    logradouro = 'Rua das Tesouras',
    numero = '123',
    bairro = 'Centro',
    cidade = 'Campinas',
    uf = 'SP',
    cep = '13010-000'
WHERE id = 1;

-- Cores em tons claros de azul/ciano/indigo (ver PALETA_CORES_AGENDA em
-- profissionais-formulario.ts) - "Cor na agenda" nao e' mais um seletor
-- livre, entao os dados de exemplo tambem seguem a paleta curada.
INSERT INTO profissional (nome, email, telefone, cor_agenda, comissao_percentual_padrao) VALUES
    ('Carlos Andrade', 'carlos@cortescavalinho.com.br', '(19) 98888-0001', '#90CAF9', 40.00),
    ('Rafael Souza', 'rafael@cortescavalinho.com.br', '(19) 98888-0002', '#4DD0E1', 35.00),
    ('Bruno Lima', 'bruno@cortescavalinho.com.br', '(19) 98888-0003', '#9FA8DA', 35.00);

INSERT INTO servico (nome, descricao, categoria, preco, duracao_minutos) VALUES
    ('Corte Masculino', 'Corte tradicional com maquina e tesoura', 'Corte', 50.00, 45),
    ('Corte Infantil', 'Corte para criancas ate 12 anos', 'Corte', 40.00, 30),
    ('Barba', 'Barba completa com toalha quente', 'Barba', 35.00, 30),
    ('Corte + Barba', 'Combo de corte masculino e barba', 'Combo', 80.00, 70),
    ('Sobrancelha', 'Design de sobrancelha na navalha', 'Estetica', 20.00, 15),
    ('Pezinho', 'Acabamento de nuca e contorno', 'Corte', 15.00, 15),
    ('Hidratacao Capilar', 'Hidratacao profunda dos fios', 'Estetica', 45.00, 40),
    ('Coloracao', 'Coloracao e retoque de raiz', 'Coloracao', 90.00, 60);

INSERT INTO profissional_servico (profissional_id, servico_id, comissao_percentual)
SELECT p.id, s.id, NULL
FROM profissional p
CROSS JOIN servico s
WHERE p.nome = 'Carlos Andrade'
  AND s.nome IN ('Corte Masculino', 'Corte Infantil', 'Barba', 'Corte + Barba', 'Pezinho');

INSERT INTO profissional_servico (profissional_id, servico_id, comissao_percentual)
SELECT p.id, s.id, CASE WHEN s.nome = 'Coloracao' THEN 45.00 ELSE NULL END
FROM profissional p
CROSS JOIN servico s
WHERE p.nome = 'Rafael Souza'
  AND s.nome IN ('Corte Masculino', 'Barba', 'Corte + Barba', 'Hidratacao Capilar', 'Coloracao');

INSERT INTO profissional_servico (profissional_id, servico_id, comissao_percentual)
SELECT p.id, s.id, NULL
FROM profissional p
CROSS JOIN servico s
WHERE p.nome = 'Bruno Lima'
  AND s.nome IN ('Corte Masculino', 'Corte Infantil', 'Sobrancelha', 'Pezinho');

-- Grade completa: terca a sabado, dois turnos por dia (segunda de folga,
-- padrao comum entre barbearias no Brasil).
INSERT INTO grade_horaria (profissional_id, dia_semana, hora_inicio, hora_fim)
SELECT p.id, dia, '09:00', '12:00'
FROM profissional p
CROSS JOIN generate_series(2, 6) AS dia;

INSERT INTO grade_horaria (profissional_id, dia_semana, hora_inicio, hora_fim)
SELECT p.id, dia, '13:00', '18:00'
FROM profissional p
CROSS JOIN generate_series(2, 6) AS dia;
