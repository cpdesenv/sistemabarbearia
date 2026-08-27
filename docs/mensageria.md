# Canal de mensageria (WhatsApp) — Fase 9

Este documento explica como o canal de mensageria funciona **nesta fase**
(sem nenhuma conta Meta) e o que falta fazer quando a Fase 6-META for
aberta, sob pedido explícito do cliente.

## Visão geral

O módulo `com.barbearia.mensageria` (backend) implementa o canal inteiro —
conversas, mensagens, idempotência, processamento assíncrono e painel —
usando exclusivamente um gateway simulado (`MockWhatsAppGateway`). Não há
`CloudApiWhatsAppGateway`, cliente da Graph API, nem nenhuma variável de
ambiente com token da Meta neste código: um `grep` no projeto por
`graph.facebook.com` ou token da Meta não encontra nada.

A interface `WhatsAppGateway` (`sendMessage`, `sendTemplate`,
`sendInteractive`, `sendDocument`) já está desenhada para a Cloud API caber
sem mudança de contrato — a Fase 6-META só precisa adicionar uma nova
implementação (`CloudApiWhatsAppGateway`) e trocar `whatsapp.gateway=mock`
por `whatsapp.gateway=cloudapi`, sem tocar em quem chama.

## Como o mock funciona

- **Envio** (`MockWhatsAppGateway`): não chama nenhum provedor real —
  apenas gera um `waMessageId` simulado (`mock-msg-...`) e loga em log
  estruturado. Um envio pode ser configurado para falhar sob demanda (ver
  "Simulando falha de envio" abaixo), para exercitar o outbox de
  retentativa.
- **Status simulado** (`StatusMensagemSimuladorWorker`, só existe com o
  gateway mock): mensagens SAÍDA avançam sozinhas de `ENVIADA` →
  `ENTREGUE` → `LIDA` depois de um delay configurável
  (`whatsapp.simulacao-status-delay-ms`, 10s por padrão) — simula os
  recibos de entrega/leitura que um provedor real mandaria por webhook.
- **Outbox de envio** (`MensagemEnvioOutboxWorker`): toda mensagem SAÍDA
  nasce com uma linha de outbox na mesma transação; o worker
  (`@Scheduled`) processa com backoff exponencial (até 8 tentativas antes
  de `FALHA_PERMANENTE`) — uma falha de envio nunca perde a mensagem, só
  atrasa.
- **Recebimento**: não usa outbox (não há chamada de rede envolvida) — é
  processado de forma assíncrona (`@Async`, virtual thread) logo após o
  webhook responder 200, com idempotência garantida por um índice único em
  `mensagem.wa_message_id` (reenviar o mesmo `waMessageId` é ignorado).

## Como simular uma conversa

1. Faça login no painel com um usuário autenticado (qualquer perfil).
2. Abra **Conversas → Simulador de WhatsApp** no menu (só aparece fora do
   perfil `prod` — ver "Endpoints de desenvolvimento" abaixo).
3. Informe um telefone (com ou sem `+55`) e um texto, e envie.
4. O sistema:
   - cria o cliente como rascunho se o telefone for novo
     (`origemCadastro=WHATSAPP`);
   - cria/reaproveita a conversa daquele telefone;
   - responde automaticamente com um eco (`"recebi: <texto>"`);
   - tudo aparece na tela **Conversas**, em formato de chat.

Também é possível simular via `curl`, sem passar pela tela:

```bash
curl -X POST http://localhost:8080/api/dev/whatsapp/inbound \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"telefone": "19999998888", "texto": "Oi"}'
```

### Simulando falha de envio

`POST /api/dev/whatsapp/simular-falha` arma uma falha de **uso único** no
`MockWhatsAppGateway` — o próximo envio processado pelo outbox falha (cai
em retentativa com backoff), e os envios seguintes voltam ao normal.

## Endpoints de desenvolvimento (`/api/dev/**`)

Todo o `WhatsAppDevController` (`/api/dev/status`,
`/api/dev/whatsapp/inbound`, `/api/dev/whatsapp/simular-falha`) é anotado
`@Profile("!prod")` — **o bean nem é criado** no perfil `prod`, não é um
`if` em runtime. A tela "Simulador de WhatsApp" só aparece no menu quando
`GET /api/dev/status` responde (a ausência do endpoint em produção faz a
tela ficar oculta). Prova automatizada:
`WhatsAppDevControllerDesabilitadoEmProdTest`.

## Webhook real (formato Cloud API)

Ainda que não haja provedor real, o webhook já fala o formato de payload
da Cloud API (`entry[].changes[].value.messages[]`), para a migração da
Fase 6-META ser só trocar quem chama, não o formato:

- `GET /api/webhook/whatsapp` — verificação (`hub.mode`,
  `hub.verify_token`, `hub.challenge`), no padrão da Meta.
- `POST /api/webhook/whatsapp` — recebimento, validado por
  `X-Hub-Signature-256` (HMAC-SHA256 do corpo, segredo em
  `whatsapp.webhook-secret`). Sem assinatura válida, `403` e nada é
  processado.

Ambas as rotas são públicas (sem JWT) porque quem chama é o provedor, não
um usuário logado — a autorização é a assinatura HMAC, não uma sessão.

## Checklist para a Fase 6-META (ativação da WhatsApp Cloud API)

Quando o cliente tiver conta Meta Business aprovada:

- [ ] Criar `CloudApiWhatsAppGateway implements WhatsAppGateway`, chamando
  a Graph API de verdade (token, número de telefone dedicado).
- [ ] Adicionar `whatsapp.gateway=cloudapi` como opção válida (hoje só
  `mock` existe) e as variáveis de ambiente do token da Meta — só nesse
  momento, nunca antes.
- [ ] Configurar o webhook real no painel da Meta apontando para
  `/api/webhook/whatsapp` (a verificação `GET` já está pronta).
- [ ] Trocar `whatsapp.webhook-secret` pelo segredo gerado pela Meta para
  aquele app.
- [ ] Revisar se `StatusMensagemSimuladorWorker` deve ser desligado (ele já
  só roda com o gateway mock — `@ConditionalOnProperty` — mas confirme que
  os recibos de entrega/leitura reais chegam por outro caminho antes de
  remover o worker de vez).
- [ ] Esconder ou remover a tela "Simulador de WhatsApp" e o
  `WhatsAppDevController` do ambiente de produção real (já são
  `@Profile("!prod")`, então isso é automático — só vale revisar se algum
  ambiente de staging usado por clientes reais também deveria virar
  "prod" para este efeito).
- [ ] Rodar a suíte completa apontando para o gateway real (ambiente de
  homologação da Meta) antes de liberar para o cliente final.
