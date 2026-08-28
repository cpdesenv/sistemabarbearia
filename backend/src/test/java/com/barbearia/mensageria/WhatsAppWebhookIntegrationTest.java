package com.barbearia.mensageria;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.cliente.domain.OrigemCadastro;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.ia.gateway.MockAiAgentGateway;
import com.barbearia.ia.gateway.RespostaAgenteIa;
import com.barbearia.mensageria.repository.ConversaRepository;
import com.barbearia.mensageria.repository.MensagemEnvioOutboxRepository;
import com.barbearia.mensageria.repository.MensagemRepository;
import com.barbearia.mensageria.service.MensagemEnvioOutboxWorker;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

/**
 * Cobre o webhook real (formato Cloud API) com o {@code MockWhatsAppGateway}
 * (padrao em teste, sem nenhuma credencial): verificacao, assinatura HMAC,
 * idempotencia por waMessageId, eco automatico e vinculacao do cliente novo.
 */
@Transactional
class WhatsAppWebhookIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ConversaRepository conversaRepository;
    @Autowired
    private MensagemRepository mensagemRepository;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private MensagemEnvioOutboxWorker envioOutboxWorker;
    @Autowired
    private MensagemEnvioOutboxRepository outboxRepository;
    @Autowired
    private MockAiAgentGateway mockAiAgentGateway;

    @Value("${whatsapp.webhook-secret}")
    private String segredoWebhook;

    private static final String SENHA = "SenhaForte123!";

    @Test
    void verificacaoDeveResponderComOChallengeQuandoTokenValido() throws Exception {
        mockMvc.perform(get("/api/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", segredoWebhook)
                        .param("hub.challenge", "desafio-123"))
                .andExpect(status().isOk())
                .andExpect(content().string("desafio-123"));
    }

    @Test
    void verificacaoComTokenInvalidoDeveRetornar403() throws Exception {
        mockMvc.perform(get("/api/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "token-errado")
                        .param("hub.challenge", "desafio-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mensagemComAssinaturaInvalidaDeveSer403ENaoProcessarNada() throws Exception {
        String corpo = montarPayload("wamid.recusado", "5519999990001", "Oi");

        mockMvc.perform(post("/api/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=assinatura-invalida")
                        .content(corpo))
                .andExpect(status().isForbidden());

        assertThat(mensagemRepository.existsByWaMessageId("wamid.recusado")).isFalse();
    }

    @Test
    void mensagemValidaDeveCriarClienteConversaEAgenteDeIaResponde() throws Exception {
        String telefoneBruto = "5519" + numeroUnico();
        String telefoneE164 = "+55" + telefoneBruto.substring(2);
        String waMessageId = "wamid." + UUID.randomUUID();
        String corpo = montarPayload(waMessageId, telefoneBruto, "Oi, tudo bem?");

        mockAiAgentGateway.programar(telefoneE164,
                new RespostaAgenteIa("Oi! Em que posso ajudar? 😊", List.of(), 10, 5));

        mockMvc.perform(post("/api/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", assinar(corpo))
                        .content(corpo))
                .andExpect(status().isOk());

        // Processamento e assincrono (virtual thread) — espera a mensagem de entrada aparecer.
        aguardarAte(() -> mensagemRepository.existsByWaMessageId(waMessageId));

        var conversa = conversaRepository.findByTelefoneE164(telefoneE164).orElseThrow();
        assertThat(conversa.getCliente().getOrigemCadastro()).isEqualTo(OrigemCadastro.WHATSAPP);
        assertThat(clienteRepository.findByTelefone(telefoneE164)).isPresent();

        // Processa so a linha de outbox desta conversa (processarPendencias() varreria
        // TODAS as linhas pendentes globais, incluindo eventuais sobras de outros
        // metodos de teste cujo status foi revertido pelo rollback do @Transactional
        // mas cuja criacao (assincrona) ja havia sido commitada de verdade).
        Long outboxId = outboxRepository.findAll().stream()
                .filter(o -> o.getMensagem().getConversa().getId().equals(conversa.getId()))
                .findFirst().orElseThrow().getId();
        envioOutboxWorker.processarUm(outboxId);

        String token = autenticar("admin.mensageria@teste.com", ipUnico());
        String resposta = mockMvc.perform(get("/api/conversas/" + conversa.getUuidPublico() + "/mensagens")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].direcao").value("ENTRADA"))
                .andExpect(jsonPath("$[0].conteudo").value("Oi, tudo bem?"))
                .andExpect(jsonPath("$[1].direcao").value("SAIDA"))
                .andExpect(jsonPath("$[1].conteudo").value("Oi! Em que posso ajudar? 😊"))
                .andExpect(jsonPath("$[1].status").value("ENVIADA"))
                .andReturn().getResponse().getContentAsString();

        assertThat(resposta).isNotBlank();
    }

    /**
     * Regressao: processarPendencias() e' quem o @Scheduled real chama, numa
     * thread do agendador sem NENHUMA transacao ambiente. Rodar o worker a
     * partir da propria thread do teste (que ja tem uma transacao aberta,
     * herdada do @Transactional da classe) mascararia o classico
     * "self-invocation problem" do Spring (this.processarUm(id), chamado de
     * dentro de processarPendencias(), nao passa pelo proxy transacional) —
     * por isso este teste roda deliberadamente numa thread separada, sem
     * transacao nenhuma, do jeito que o agendador de verdade faz.
     */
    @Test
    void processarPendenciasDeveFuncionarSemTransacaoAmbienteComoOSchedulerReal() throws Exception {
        String telefoneBruto = "5519" + numeroUnico();
        String telefoneE164 = "+55" + telefoneBruto.substring(2);
        String waMessageId = "wamid." + UUID.randomUUID();
        String corpo = montarPayload(waMessageId, telefoneBruto, "Mensagem via scheduler");

        mockAiAgentGateway.programar(telefoneE164, new RespostaAgenteIa("Resposta via scheduler", List.of(), 10, 5));

        mockMvc.perform(post("/api/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", assinar(corpo))
                        .content(corpo))
                .andExpect(status().isOk());

        aguardarAte(() -> mensagemRepository.existsByWaMessageId(waMessageId));

        CompletableFuture.runAsync(envioOutboxWorker::processarPendencias).get(5, TimeUnit.SECONDS);

        var conversa = conversaRepository.findByTelefoneE164(telefoneE164).orElseThrow();
        var respostaDaIa = mensagemRepository.findByConversaOrderByCriadoEmAsc(conversa).stream()
                .filter(m -> "Resposta via scheduler".equals(m.getConteudo()))
                .findFirst().orElseThrow();
        assertThat(respostaDaIa.getStatus().name()).isEqualTo("ENVIADA");
    }

    @Test
    void reenviarOMesmoPayloadNaoDeveDuplicarMensagem() throws Exception {
        String telefoneBruto = "5519" + numeroUnico();
        String waMessageId = "wamid." + UUID.randomUUID();
        String corpo = montarPayload(waMessageId, telefoneBruto, "Mensagem repetida");
        String assinatura = assinar(corpo);

        mockMvc.perform(post("/api/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", assinatura)
                        .content(corpo))
                .andExpect(status().isOk());

        aguardarAte(() -> mensagemRepository.existsByWaMessageId(waMessageId));

        // Reenvio do mesmo payload (mesmo waMessageId) — simulando o provedor retentando a entrega.
        mockMvc.perform(post("/api/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", assinatura)
                        .content(corpo))
                .andExpect(status().isOk());

        Thread.sleep(500);

        long quantidade = mensagemRepository.findAll().stream()
                .filter(m -> waMessageId.equals(m.getWaMessageId()))
                .count();
        assertThat(quantidade).isEqualTo(1);
    }

    /** Espera o processamento assincrono (virtual thread) terminar, com timeout de 5s. */
    private void aguardarAte(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5000;
        while (!condicao.getAsBoolean()) {
            if (System.currentTimeMillis() > limite) {
                throw new AssertionError("Condicao nao atingida apos 5 segundos.");
            }
            Thread.sleep(100);
        }
    }

    private String montarPayload(String waMessageId, String telefone, String texto) {
        return """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "id": "%s",
                          "from": "%s",
                          "type": "text",
                          "text": { "body": "%s" }
                        }]
                      }
                    }]
                  }]
                }
                """.formatted(waMessageId, telefone, texto);
    }

    /** 9 digitos (estilo celular) — combinado com o DDD "5519" da fixo do teste, forma um numero de 13 digitos, o formato que o TelefoneNormalizador reconhece como "55 + DDD + numero". */
    /** IP simulado unico por chamada — o bucket de rate limiting e' global ao contexto Spring
     * (compartilhado por toda a suite via cache de contexto), entao um IP fixo colidiria com
     * qualquer outro teste (desta ou de outra classe) que reusasse o mesmo valor. */
    private String ipUnico() {
        return UUID.randomUUID().toString();
    }

    private String numeroUnico() {
        return String.format("9%08d", (int) (Math.random() * 100_000_000));
    }

    private String assinar(String corpo) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(segredoWebhook.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    private String autenticar(String email, String ipSimulado) throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuario de teste");
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPerfil(Perfil.ADMIN);
        usuario.setAtivo(true);
        usuarioRepository.save(usuario);

        String corpoLogin = mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ipSimulado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, SENHA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(corpoLogin).get("accessToken").asText();
    }
}
