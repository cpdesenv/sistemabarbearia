# Limitações conhecidas e fora de escopo

Este documento registra decisões de escopo tomadas conscientemente, para que
não sejam reabertas por engano em fases futuras.

## Sem multi-tenant

O sistema atende **uma única barbearia**. Não existe `barbearia_id` como
discriminador de tenant em tabelas de negócio, não existe resolução de tenant
por subdomínio/header/claim de JWT, e não existe Row Level Security para
isolamento. A tabela `barbearia` é um singleton de configuração (uma linha,
criada por migration, editável no painel, nunca listada nem criada pela API).

Se em algum momento surgir a necessidade de atender mais de uma barbearia,
isso é uma decisão de negócio consciente que exige um novo projeto de
arquitetura — não uma extensão silenciosa deste sistema.

## IA e WhatsApp fora do escopo

O canal de mensageria via WhatsApp e o agente de atendimento por IA (antigas
Fases 9, 6-META, 10 e 11) foram construídos, validados e depois **removidos
por decisão do cliente**, que optou por não utilizar essas funcionalidades.
Não se trata mais de "mock até a conta Meta existir" — o código, as tabelas
e a documentação desses módulos foram desativados. Ver `CHANGELOG.md` (entrada
da remoção) para o que foi retirado e por quê. Se o cliente decidir revisitar
atendimento via WhatsApp/IA no futuro, isso é uma decisão de produto nova, a
ser especificada do zero — não uma reativação do que existia.

## Spring Boot 3.5.x — fim de vida open-source

A linha 3.5.x do Spring Boot atingiu o fim de vida open-source em 30/06/2026
(última versão: 3.5.16). O projeto usa essa versão por decisão explícita do
escopo (Java 21 + Spring Boot 3.5.x), mas isso significa que não haverá mais
patches de segurança gratuitos para essa linha. A migração para uma linha
suportada deve ser tratada como um item de trabalho futuro deliberado,
avaliado quando fizer sentido para o projeto — não uma troca silenciosa.

## Tema visual provisório

Desde a Fase 1 o painel usa Angular Material com um tema Material 3 (ver
`frontend/src/styles.scss`), mas com uma paleta neutra provisória — a
identidade visual definitiva (logo, cores da marca) só é providenciada pelo
usuário na Fase 19. Trocar a paleta é uma alteração isolada nesse arquivo,
sem impacto em nenhum componente.
