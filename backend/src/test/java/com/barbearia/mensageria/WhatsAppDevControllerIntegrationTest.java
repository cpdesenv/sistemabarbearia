package com.barbearia.mensageria;

import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.ia.gateway.MockAiAgentGateway;
import com.barbearia.ia.gateway.RespostaAgenteIa;
import com.barbearia.mensageria.domain.StatusEnvioOutbox;
import com.barbearia.mensageria.domain.StatusMensagem;
import com.barbearia.mensageria.repository.ConversaRepository;
import com.barbearia.mensageria.repository.MensagemEnvioOutboxRepository;
import com.barbearia.mensageria.repository.MensagemRepository;
import com.barbearia.mensageria.service.MensagemEnvioOutboxWorker;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

/**
 * Cobre o simulador de WhatsApp (endpoints {@code /api/dev/**}, existentes
 * porque a suite roda fora do perfil {@code prod} — ver
 * {@link WhatsAppDevControllerDesabilitadoEmProdTest} para a prova de que
 * eles somem em producao).
 */
@Transactional
class WhatsAppDevControllerIntegrationTest extends IntegrationTestBase {

    private static final String SENHA = "SenhaForte123!";

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
    private MensagemEnvioOutboxRepository outboxRepository;
    @Autowired
    private MensagemEnvioOutboxWorker envioOutboxWorker;
    @Autowired
    private MockAiAgentGateway mockAiAgentGateway;

    @Test
    void statusDeveExigirAutenticacao() throws Exception {
        mockMvc.perform(get("/api/dev/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void statusDeveInformarHabilitado() throws Exception {
        String token = autenticar("admin.devstatus@teste.com", ipUnico());

        mockMvc.perform(get("/api/dev/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitado").value(true));
    }

    @Test
    void injetarMensagemDeveConduzirUmaConversaCompletaSemCredencialExterna() throws Exception {
        String token = autenticar("admin.devinbound@teste.com", ipUnico());
        String telefone = "5519" + numeroUnico();
        String telefoneE164 = "+55" + telefone.substring(2);

        mockAiAgentGateway.programar(telefoneE164, new RespostaAgenteIa("Oi! Tudo bem?", List.of(), 10, 5));

        mockMvc.perform(post("/api/dev/whatsapp/inbound")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\": \"" + telefone + "\", \"texto\": \"Ola do simulador\"}"))
                .andExpect(status().isAccepted());

        aguardarAte(() -> conversaRepository.findByTelefoneE164(telefoneE164).isPresent());
        var conversa = conversaRepository.findByTelefoneE164(telefoneE164).orElseThrow();

        // Processa so a linha desta conversa (processarPendencias() varreria todas as
        // linhas pendentes globais, incluindo sobras de outros testes — ver
        // WhatsAppWebhookIntegrationTest para a explicacao completa).
        envioOutboxWorker.processarUm(idDoOutboxDaConversa(conversa.getId()));

        mockMvc.perform(get("/api/conversas/" + conversa.getUuidPublico() + "/mensagens")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].conteudo").value("Oi! Tudo bem?"))
                .andExpect(jsonPath("$[1].status").value("ENVIADA"));
    }

    @Test
    void simularFalhaDeveCairNoOutboxERetentarComSucessoDepois() throws Exception {
        String token = autenticar("admin.devfalha@teste.com", ipUnico());
        String telefone = "5519" + numeroUnico();

        mockMvc.perform(post("/api/dev/whatsapp/simular-falha")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/dev/whatsapp/inbound")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\": \"" + telefone + "\", \"texto\": \"Vai falhar\"}"))
                .andExpect(status().isAccepted());

        String telefoneE164 = "+55" + telefone.substring(2);
        aguardarAte(() -> conversaRepository.findByTelefoneE164(telefoneE164).isPresent());
        var conversa = conversaRepository.findByTelefoneE164(telefoneE164).orElseThrow();
        Long outboxId = idDoOutboxDaConversa(conversa.getId());
        var mensagemSaida = outboxRepository.findById(outboxId).orElseThrow().getMensagem();

        // Primeira tentativa: falha simulada (armada acima) — outbox continua PENDENTE.
        // Processa so esta linha (nao processarPendencias(), que varreria todas as
        // linhas pendentes globais, incluindo sobras de outros testes).
        envioOutboxWorker.processarUm(outboxId);
        assertThat(mensagemRepository.findById(mensagemSaida.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusMensagem.PENDENTE);
        var linhaOutbox = outboxRepository.findById(outboxId).orElseThrow();
        assertThat(linhaOutbox.getStatus()).isEqualTo(StatusEnvioOutbox.PENDENTE);
        assertThat(linhaOutbox.getTentativas()).isEqualTo(1);

        // Forca a proxima tentativa a rodar imediatamente (sem esperar o backoff de verdade).
        linhaOutbox.setProximaTentativaEm(java.time.Instant.now());
        outboxRepository.save(linhaOutbox);

        envioOutboxWorker.processarUm(outboxId);
        assertThat(mensagemRepository.findById(mensagemSaida.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusMensagem.ENVIADA);
    }

    private Long idDoOutboxDaConversa(Long conversaId) {
        return outboxRepository.findAll().stream()
                .filter(o -> o.getMensagem().getConversa().getId().equals(conversaId))
                .findFirst().orElseThrow().getId();
    }

    private void aguardarAte(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5000;
        while (!condicao.getAsBoolean()) {
            if (System.currentTimeMillis() > limite) {
                throw new AssertionError("Condicao nao atingida apos 5 segundos.");
            }
            Thread.sleep(100);
        }
    }

    private String numeroUnico() {
        return String.format("9%08d", (int) (Math.random() * 100_000_000));
    }

    /** IP simulado unico por chamada — o bucket de rate limiting de login e' global ao
     * contexto Spring (compartilhado por toda a suite via cache de contexto). */
    private String ipUnico() {
        return UUID.randomUUID().toString();
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
