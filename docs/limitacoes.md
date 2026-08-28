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

## Sem integração de WhatsApp/IA

Registrado em 2026-08-28. As antigas Fase 9 (canal de mensageria com
`MockWhatsAppGateway`) e Fases 10/11 (agente de IA de atendimento,
agendamento, cancelamento e remarcação) foram implementadas, entregues e
validadas — e depois **removidas por completo** do sistema (código,
migrations, telas e dependências), por decisão de custo: a tarifação da API
da Meta tornou a integração de WhatsApp inviável para o projeto, e o agente
de IA só existia para conduzir conversas por esse canal, então deixou de
fazer sentido sem ele.

O sistema não tem mais nenhum canal de mensageria automatizada nem agente de
IA. O agendamento pelo cliente final acontece pelo portal público de
autoagendamento (link direto, sem canal de conversa) ou por atendimento
direto da equipe (telefone/presencial, fora do sistema). Confirmações são
enviadas por e-mail.

Esta é uma decisão de escopo fechada — não reabrir sem pedido explícito. Se
no futuro fizer sentido reintroduzir um canal de mensageria ou um agente de
IA, isso é um projeto novo, com decisão consciente de negócio, não uma
retomada silenciosa do que foi removido.

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
