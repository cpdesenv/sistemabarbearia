package com.barbearia.portal;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

@Transactional
class PortalControllerIntegrationTest extends IntegrationTestBase {

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
    private ClienteRepository clienteRepository;

    @Test
    void catalogoEDisponibilidadeNaoExigemAutenticacao() throws Exception {
        String token = autenticar("admin.catalogo@teste.com", "203.0.113.1");
        LocalDate data = proximaSegunda();
        UUID servicoUuid = criarServico(token, "Corte Portal", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Portal Catalogo");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        mockMvc.perform(get("/api/portal/servicos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.uuid=='" + servicoUuid + "')]").exists());

        mockMvc.perform(get("/api/portal/profissionais").param("servicoUuids", servicoUuid.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.uuid=='" + profissionalUuid + "')]").exists());

        mockMvc.perform(get("/api/portal/disponibilidade")
                        .param("data", data.toString())
                        .param("servicoUuids", servicoUuid.toString())
                        .param("profissionalUuid", profissionalUuid.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void deveCriarAgendamentoConfirmadoParaClienteNovoEEnviarEmail() throws Exception {
        String token = autenticar("admin.novo@teste.com", "203.0.113.2");
        LocalDate data = proximaSegunda();
        UUID servicoUuid = criarServico(token, "Corte Novo Cliente", 45, "60.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Portal Novo");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        Instant inicio = ZonedDateTime.of(data, LocalTime.of(10, 0), FUSO).toInstant();

        MvcResult resultado = mockMvc.perform(post("/api/portal/agendamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPortal("Cliente Portal Novo", "(19) 98888-1234", "cliente@teste.com",
                                profissionalUuid, List.of(servicoUuid), inicio, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.clienteUuid").doesNotExist())
                .andReturn();

        JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
        assertThat(corpo.get("clienteNome").asText()).isEqualTo("Cliente Portal Novo");
        assertThat(clienteRepository.findByTelefone("+5519988881234")).isPresent();
        assertThat(clienteRepository.findByTelefone("+5519988881234").get().getOrigemCadastro().name())
                .isEqualTo("PORTAL");

        mockMvc.perform(get("/api/agendamentos/" + corpo.get("uuid").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origem").value("PORTAL"));
    }

    @Test
    void deveReaproveitarClienteExistentePeloTelefoneSemDuplicar() throws Exception {
        String token = autenticar("admin.existente@teste.com", "203.0.113.3");
        LocalDate data = proximaSegunda();
        UUID clienteUuid = criarCliente(token, "Cliente Ja Cadastrado", "(19) 97777-5555");
        UUID servicoUuid = criarServico(token, "Corte Existente", 30, "40.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Portal Existente");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        Instant inicio = ZonedDateTime.of(data, LocalTime.of(11, 0), FUSO).toInstant();

        MvcResult resultado = mockMvc.perform(post("/api/portal/agendamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPortal("Nome Diferente Informado", "(19) 97777-5555", null,
                                profissionalUuid, List.of(servicoUuid), inicio, true)))
                .andExpect(status().isCreated())
                // Resposta publica devolve o nome DIGITADO pelo solicitante, nunca o nome
                // gravado no cadastro (evita virar oraculo de PII de terceiros) — nem
                // clienteUuid/clienteTelefone, que nao tem por que ir a um chamador anonimo.
                .andExpect(jsonPath("$.clienteNome").value("Nome Diferente Informado"))
                .andExpect(jsonPath("$.clienteUuid").doesNotExist())
                .andReturn();

        // Conta so' pelo telefone deste teste, nunca a tabela inteira: a suite
        // roda tudo contra o mesmo container Postgres (IntegrationTestBase,
        // "singleton container pattern"), e alguns testes de concorrencia
        // (ex.: AssinaturaSaldoConcorrenciaTest) commitam de verdade fora do
        // rollback transacional deste teste, entao a contagem global de
        // clientes varia conforme a ordem de execucao das classes.
        assertThat(clienteRepository.findByTelefone("+5519977775555")).isPresent();
        assertThat(clienteRepository.findByUuidPublico(clienteUuid).get().getNome()).isEqualTo("Cliente Ja Cadastrado");

        // O agendamento de verdade continua vinculado ao cliente ja cadastrado
        // (mesmo telefone), so' a resposta publica que nao expoe o nome real.
        String tokenVerificacao = autenticar("admin.verifica-vinculo@teste.com", "203.0.113.99");
        JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
        String uuidAgendamento = corpo.get("uuid").asText();
        mockMvc.perform(get("/api/agendamentos/" + uuidAgendamento)
                        .header("Authorization", "Bearer " + tokenVerificacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteUuid").value(clienteUuid.toString()))
                .andExpect(jsonPath("$.clienteNome").value("Cliente Ja Cadastrado"));
    }

    @Test
    void deveExigirConsentimentoLgpdParaClienteNovo() throws Exception {
        String token = autenticar("admin.lgpd@teste.com", "203.0.113.4");
        LocalDate data = proximaSegunda();
        UUID servicoUuid = criarServico(token, "Corte LGPD", 30, "40.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Portal LGPD");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        Instant inicio = ZonedDateTime.of(data, LocalTime.of(12, 0), FUSO).toInstant();

        mockMvc.perform(post("/api/portal/agendamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPortal("Sem Consentimento", "(19) 96666-4444", null,
                                profissionalUuid, List.of(servicoUuid), inicio, false)))
                .andExpect(status().isBadRequest());

        assertThat(clienteRepository.findByTelefone("+5519966664444")).isEmpty();
    }

    @Test
    void deveRecusarAgendamentoEmHorarioJaOcupado() throws Exception {
        String token = autenticar("admin.conflito@teste.com", "203.0.113.5");
        LocalDate data = proximaSegunda();
        UUID servicoUuid = criarServico(token, "Corte Conflito", 30, "40.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Portal Conflito");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        Instant inicio = ZonedDateTime.of(data, LocalTime.of(13, 0), FUSO).toInstant();

        mockMvc.perform(post("/api/portal/agendamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPortal("Primeiro Cliente", "(19) 95555-1111", null,
                                profissionalUuid, List.of(servicoUuid), inicio, true)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/portal/agendamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPortal("Segundo Cliente", "(19) 94444-2222", null,
                                profissionalUuid, List.of(servicoUuid), inicio, true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void portalDesativadoRecusaConsultasECriacao() throws Exception {
        String token = autenticar("admin.desativado@teste.com", "203.0.113.6");
        LocalDate data = proximaSegunda();
        UUID servicoUuid = criarServico(token, "Corte Desativado", 30, "40.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Portal Desativado");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        desativarPortal(token);

        mockMvc.perform(get("/api/portal/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(get("/api/portal/servicos"))
                .andExpect(status().isNotFound());

        Instant inicio = ZonedDateTime.of(data, LocalTime.of(14, 0), FUSO).toInstant();
        mockMvc.perform(post("/api/portal/agendamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPortal("Cliente Portal Off", "(19) 93333-0000", null,
                                profissionalUuid, List.of(servicoUuid), inicio, true)))
                .andExpect(status().isNotFound());
    }

    private void desativarPortal(String token) throws Exception {
        String corpo = """
                {
                  "nome": "Minha Barbearia",
                  "cnpj": null,
                  "telefone": null,
                  "email": null,
                  "logradouro": null,
                  "numero": null,
                  "complemento": null,
                  "bairro": null,
                  "cidade": null,
                  "uf": null,
                  "cep": null,
                  "fusoHorario": "America/Sao_Paulo",
                  "antecedenciaMinimaAgendamentoMinutos": 0,
                  "antecedenciaMaximaAgendamentoDias": 60,
                  "antecedenciaMinimaCancelamentoMinutos": 120,
                  "granularidadeSlotMinutos": 15,
                  "portalAgendamentoAtivo": false
                }
                """;
        mockMvc.perform(put("/api/barbearia")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());
    }

    private String corpoPortal(String nome, String telefone, String email, UUID profissionalUuid,
            List<UUID> servicoUuids, Instant inicio, boolean consentimentoLgpd) {
        String servicosJson = servicoUuids.stream().map(uuid -> "\"" + uuid + "\"")
                .reduce((a, b) -> a + "," + b).orElse("");
        String emailJson = email == null ? "null" : "\"" + email + "\"";
        return """
                {
                  "nome": "%s",
                  "telefone": "%s",
                  "email": %s,
                  "profissionalUuid": "%s",
                  "servicoUuids": [%s],
                  "inicio": "%s",
                  "consentimentoLgpd": %s
                }
                """.formatted(nome, telefone, emailJson, profissionalUuid, servicosJson,
                DateTimeFormatter.ISO_INSTANT.format(inicio), consentimentoLgpd);
    }

    private LocalDate proximaSegunda() {
        return ZonedDateTime.now(FUSO).toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private UUID criarCliente(String token, String nome, String telefone) throws Exception {
        String corpo = """
                {
                  "nome": "%s",
                  "telefone": "%s",
                  "optInWhatsapp": true,
                  "consentimentoLgpd": true
                }
                """.formatted(nome, telefone);

        String resposta = mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
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
                  "email": "profissional@teste.com",
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
