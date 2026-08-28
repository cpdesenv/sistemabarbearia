package com.barbearia.autoagendamento;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.fiscal.email.EmailGateway;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

/**
 * Fase 9 — link publico de autoagendamento. Reaproveita o mesmo padrao de
 * fixtures (servico/profissional/grade via API autenticada) das demais
 * suites de agenda, mas o fluxo testado (GET/POST /api/autoagendamento/**)
 * e' sempre chamado SEM nenhum header de autorizacao — e' exatamente isso
 * que o PRD pede ("cliente acessa URL publica e agenda sozinho").
 */
@Transactional
class AutoagendamentoControllerIntegrationTest extends IntegrationTestBase {

    private static final String SENHA = "SenhaForte123!";
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private BarbeariaRepository barbeariaRepository;
    @MockitoBean
    private EmailGateway emailGateway;

    @Test
    void consultaDisponibilidadeSemAutenticacaoEDevolveHorariosReais() throws Exception {
        String token = autenticar("admin.autoagendamento.disp@teste.com");
        UUID profissional = criarProfissional(token, unico("Prof Portal"));
        UUID servico = criarServico(token, unico("Servico Portal"), 30, "40.00");
        vincularServico(token, profissional, servico);
        LocalDate dia = proximaSegunda();
        sincronizarGrade(token, profissional, dia.getDayOfWeek().getValue(), "09:00", "18:00");

        mockMvc.perform(get("/api/autoagendamento/disponibilidade")
                        .param("data", dia.toString())
                        .param("servicoUuids", servico.toString())
                        .param("profissionalUuid", profissional.toString()))
                // sem Authorization nenhum — a rota e' publica
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode slots = objectMapper.readTree(result.getResponse().getContentAsString());
                    assertThat(slots.isArray()).isTrue();
                    assertThat(slots.size()).isGreaterThan(0);
                    assertThat(slots.get(0).get("profissionalUuid").asText()).isEqualTo(profissional.toString());
                });
    }

    @Test
    void listaServicosEProfissionaisPublicosSemVazarDadosInternos() throws Exception {
        String token = autenticar("admin.autoagendamento.publico@teste.com");
        criarProfissional(token, unico("Prof Publico"));
        criarServico(token, unico("Servico Publico"), 30, "40.00");

        mockMvc.perform(get("/api/autoagendamento/profissionais"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode profissionais = objectMapper.readTree(result.getResponse().getContentAsString());
                    assertThat(profissionais.size()).isGreaterThan(0);
                    JsonNode primeiro = profissionais.get(0);
                    assertThat(primeiro.has("email")).isFalse();
                    assertThat(primeiro.has("telefone")).isFalse();
                    assertThat(primeiro.has("comissaoPercentualPadrao")).isFalse();
                });

        mockMvc.perform(get("/api/autoagendamento/servicos"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode servicos = objectMapper.readTree(result.getResponse().getContentAsString());
                    assertThat(servicos.size()).isGreaterThan(0);
                });
    }

    @Test
    void agendoPeloLinkPublicoCriaAgendamentoConfirmadoEEnviaConfirmacaoPorEmail() throws Exception {
        String token = autenticar("admin.autoagendamento.criar@teste.com");
        UUID profissional = criarProfissional(token, unico("Prof Autoagendamento"));
        UUID servico = criarServico(token, unico("Servico Autoagendamento"), 30, "40.00");
        vincularServico(token, profissional, servico);
        LocalDate dia = proximaSegunda();
        sincronizarGrade(token, profissional, dia.getDayOfWeek().getValue(), "09:00", "18:00");
        Instant inicio = ZonedDateTime.of(dia, LocalTime.of(9, 0), FUSO).toInstant();
        String telefone = novoTelefone();

        String corpo = """
                {
                  "nome": "Cliente Portal",
                  "telefone": "%s",
                  "email": "cliente.portal@teste.com",
                  "consentimentoLgpd": true,
                  "profissionalUuid": "%s",
                  "servicoUuids": ["%s"],
                  "inicio": "%s"
                }
                """.formatted(telefone, profissional, servico, inicio);

        String resposta = mockMvc.perform(post("/api/autoagendamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode agendamento = objectMapper.readTree(resposta);
        assertThat(agendamento.get("status").asText()).isEqualTo("CONFIRMADO");
        assertThat(agendamento.get("origem").asText()).isEqualTo("PORTAL");

        verify(emailGateway).enviarConfirmacaoAgendamento(eq("cliente.portal@teste.com"), eq("Cliente Portal"),
                any());
    }

    @Test
    void agendoSemInformarEmailNaoDisparaEnvioDeEmail() throws Exception {
        String token = autenticar("admin.autoagendamento.sememail@teste.com");
        UUID profissional = criarProfissional(token, unico("Prof Sem Email"));
        UUID servico = criarServico(token, unico("Servico Sem Email"), 30, "40.00");
        vincularServico(token, profissional, servico);
        LocalDate dia = proximaSegunda();
        sincronizarGrade(token, profissional, dia.getDayOfWeek().getValue(), "09:00", "18:00");
        Instant inicio = ZonedDateTime.of(dia, LocalTime.of(9, 0), FUSO).toInstant();
        String telefone = novoTelefone();

        String corpo = """
                {
                  "nome": "Cliente Sem Email",
                  "telefone": "%s",
                  "email": null,
                  "consentimentoLgpd": true,
                  "profissionalUuid": "%s",
                  "servicoUuids": ["%s"],
                  "inicio": "%s"
                }
                """.formatted(telefone, profissional, servico, inicio);

        mockMvc.perform(post("/api/autoagendamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated());

        verify(emailGateway, never()).enviarConfirmacaoAgendamento(any(), any(), any());
    }

    @Test
    void semAceitarLgpdNaoCriaAgendamento() throws Exception {
        String token = autenticar("admin.autoagendamento.lgpd@teste.com");
        UUID profissional = criarProfissional(token, unico("Prof Lgpd"));
        UUID servico = criarServico(token, unico("Servico Lgpd"), 30, "40.00");
        vincularServico(token, profissional, servico);
        LocalDate dia = proximaSegunda();
        sincronizarGrade(token, profissional, dia.getDayOfWeek().getValue(), "09:00", "18:00");
        Instant inicio = ZonedDateTime.of(dia, LocalTime.of(9, 0), FUSO).toInstant();

        String corpo = """
                {
                  "nome": "Cliente Sem Lgpd",
                  "telefone": "%s",
                  "email": null,
                  "consentimentoLgpd": false,
                  "profissionalUuid": "%s",
                  "servicoUuids": ["%s"],
                  "inicio": "%s"
                }
                """.formatted(novoTelefone(), profissional, servico, inicio);

        mockMvc.perform(post("/api/autoagendamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void linkDesativadoGlobalmenteFazTodasAsRotasResponderemIndisponivel() throws Exception {
        String token = autenticar("admin.autoagendamento.killswitch@teste.com");
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON).orElseThrow();
        barbearia.setPortalAutoagendamentoAtivo(false);
        barbeariaRepository.save(barbearia);

        try {
            mockMvc.perform(get("/api/autoagendamento/configuracao"))
                    .andExpect(status().isOk())
                    .andExpect(result -> {
                        JsonNode configuracao = objectMapper.readTree(result.getResponse().getContentAsString());
                        assertThat(configuracao.get("ativo").asBoolean()).isFalse();
                    });

            mockMvc.perform(get("/api/autoagendamento/servicos")).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/autoagendamento/profissionais")).andExpect(status().isNotFound());
        } finally {
            Barbearia paraRestaurar = barbeariaRepository.findById(Barbearia.ID_SINGLETON).orElseThrow();
            paraRestaurar.setPortalAutoagendamentoAtivo(true);
            barbeariaRepository.save(paraRestaurar);
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private LocalDate proximaSegunda() {
        return ZonedDateTime.now(FUSO).toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private String unico(String base) {
        return base + " Portal-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String novoTelefone() {
        return "5519" + String.format("9%08d", (int) (Math.random() * 100_000_000));
    }

    private UUID criarServico(String token, String nome, int duracaoMinutos, String preco) throws Exception {
        String corpo = """
                {
                  "nome": "%s",
                  "descricao": "Descricao de teste",
                  "categoria": "Corte",
                  "preco": %s,
                  "duracaoMinutos": %d
                }
                """.formatted(nome, preco, duracaoMinutos);

        String resposta = mockMvc.perform(post("/api/servicos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
    }

    private UUID criarProfissional(String token, String nome) throws Exception {
        String corpo = """
                {
                  "nome": "%s",
                  "email": "profissional.portal@teste.com",
                  "telefone": "11900000000",
                  "corAgenda": "#3F51B5",
                  "comissaoPercentualPadrao": 30.00
                }
                """.formatted(nome);

        String resposta = mockMvc.perform(post("/api/profissionais")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
    }

    private void vincularServico(String token, UUID profissionalUuid, UUID servicoUuid) throws Exception {
        String corpo = "[{\"servicoUuid\": \"" + servicoUuid + "\", \"comissaoPercentual\": null}]";

        mockMvc.perform(put("/api/profissionais/" + profissionalUuid + "/servicos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());
    }

    private void sincronizarGrade(String token, UUID profissionalUuid, int diaSemana, String horaInicio,
            String horaFim) throws Exception {
        String corpo = "[{\"diaSemana\": " + diaSemana + ", \"horaInicio\": \"" + horaInicio + "\", \"horaFim\": \""
                + horaFim + "\"}]";

        mockMvc.perform(put("/api/profissionais/" + profissionalUuid + "/grade-horaria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());
    }

    private String autenticar(String email) throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuario de teste");
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPerfil(Perfil.ADMIN);
        usuario.setAtivo(true);
        usuarioRepository.save(usuario);

        String corpoLogin = mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, SENHA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(corpoLogin).get("accessToken").asText();
    }
}
