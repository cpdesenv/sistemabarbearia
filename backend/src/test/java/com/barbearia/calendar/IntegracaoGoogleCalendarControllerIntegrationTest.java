package com.barbearia.calendar;

import java.net.URI;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.calendar.domain.AgendamentoCalendarOutbox;
import com.barbearia.calendar.domain.StatusOutbox;
import com.barbearia.calendar.repository.AgendamentoCalendarOutboxRepository;
import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.repository.ProfissionalRepository;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

/**
 * Cobre o fluxo administrativo da integracao com o Google Calendar (Fase 8)
 * com o {@code MockGoogleOAuthGateway} (padrao em teste): conectar, callback
 * (state valido/invalido), desconectar, modo de calendario, calendario por
 * profissional e o botao de ressincronizar.
 */
@Transactional
class IntegracaoGoogleCalendarControllerIntegrationTest extends IntegrationTestBase {

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
    private ProfissionalRepository profissionalRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private AgendamentoCalendarOutboxRepository outboxRepository;

    @Test
    void deveExigirAutenticacaoParaStatus() throws Exception {
        mockMvc.perform(get("/api/integracoes/google-calendar/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statusInicialDeveMostrarNaoConectado() throws Exception {
        String token = autenticar("admin.gcalstatus@teste.com", Perfil.ADMIN, "203.0.113.20");

        mockMvc.perform(get("/api/integracoes/google-calendar/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conectado").value(false))
                .andExpect(jsonPath("$.modo").value("CALENDARIO_UNICO"));
    }

    @Test
    void apenasAdminPodeIniciarConexao() throws Exception {
        String tokenRecepcao = autenticar("recepcao.gcal@teste.com", Perfil.RECEPCAO, "203.0.113.21");

        mockMvc.perform(get("/api/integracoes/google-calendar/conectar")
                        .header("Authorization", "Bearer " + tokenRecepcao))
                .andExpect(status().isForbidden());
    }

    @Test
    void conectarDeveGerarUrlComState() throws Exception {
        String token = autenticar("admin.gcalconectar@teste.com", Perfil.ADMIN, "203.0.113.22");

        String resposta = mockMvc.perform(get("/api/integracoes/google-calendar/conectar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String url = objectMapper.readTree(resposta).get("url").asText();
        assertThat(url).contains("state=");
    }

    @Test
    void callbackComStateValidoDeveConectarERedirecionarComSucesso() throws Exception {
        String token = autenticar("admin.gcalcallback@teste.com", Perfil.ADMIN, "203.0.113.23");
        String state = extrairState(token);

        mockMvc.perform(get("/api/integracoes/google-calendar/callback")
                        .param("code", "codigo-simulado")
                        .param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("conectado=true")));

        mockMvc.perform(get("/api/integracoes/google-calendar/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conectado").value(true));
    }

    @Test
    void callbackComStateInvalidoDeveRedirecionarComErroSemConectar() throws Exception {
        String token = autenticar("admin.gcalcallbackinvalido@teste.com", Perfil.ADMIN, "203.0.113.24");

        mockMvc.perform(get("/api/integracoes/google-calendar/callback")
                        .param("code", "codigo-simulado")
                        .param("state", "state-que-nao-existe"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("erro=")));

        mockMvc.perform(get("/api/integracoes/google-calendar/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conectado").value(false));
    }

    @Test
    void desconectarDeveLimparConexao() throws Exception {
        String token = autenticar("admin.gcaldesconectar@teste.com", Perfil.ADMIN, "203.0.113.25");
        String state = extrairState(token);
        mockMvc.perform(get("/api/integracoes/google-calendar/callback")
                        .param("code", "codigo-simulado")
                        .param("state", state))
                .andExpect(status().isFound());

        mockMvc.perform(post("/api/integracoes/google-calendar/desconectar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/integracoes/google-calendar/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conectado").value(false));
    }

    @Test
    void deveAtualizarModoEDefinirCalendarioDoProfissional() throws Exception {
        String token = autenticar("admin.gcalmodo@teste.com", Perfil.ADMIN, "203.0.113.26");
        UUID profissionalUuid = criarProfissional(token, "Prof Modo Calendario");

        mockMvc.perform(put("/api/integracoes/google-calendar/modo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modo\": \"POR_PROFISSIONAL\", \"calendarioIdUnico\": null}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/integracoes/google-calendar/profissionais/" + profissionalUuid)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"googleCalendarId\": \"calendario-do-profissional@group.calendar.google.com\"}"))
                .andExpect(status().isNoContent());

        Profissional profissional = profissionalRepository.findByUuidPublico(profissionalUuid).orElseThrow();
        assertThat(profissional.getGoogleCalendarId()).isEqualTo("calendario-do-profissional@group.calendar.google.com");
    }

    @Test
    void ressincronizarDeveZerarTentativasDePendenciasEForaDeSincroniaDeveListarAntes() throws Exception {
        String token = autenticar("admin.gcalressync@teste.com", Perfil.ADMIN, "203.0.113.27");
        UUID clienteUuid = criarCliente(token, "Cliente Ressync", "(19) 90000-4444");
        UUID servicoUuid = criarServico(token, "Corte Ressync", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Ressync");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        LocalDate proximaSegunda = ZonedDateTime.now(FUSO).toLocalDate()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Instant inicio = ZonedDateTime.of(proximaSegunda, LocalTime.of(9, 0), FUSO).toInstant();
        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, servicoUuid, inicio);
        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/confirmar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Simula uma falha ja ocorrida (sem esperar o backoff real do worker).
        Long agendamentoId = agendamentoRepository.findByUuidPublico(agendamentoUuid).orElseThrow().getId();
        AgendamentoCalendarOutbox linha = outboxRepository.findByStatusNot(StatusOutbox.CONCLUIDO).stream()
                .filter(l -> l.getAgendamento().getId().equals(agendamentoId))
                .findFirst().orElseThrow();
        linha.setTentativas(3);
        linha.setProximaTentativaEm(Instant.now().plusSeconds(3600));
        linha.setUltimoErro("Falha simulada");
        outboxRepository.save(linha);

        mockMvc.perform(get("/api/integracoes/google-calendar/fora-de-sincronia")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].agendamentoUuid").value(agendamentoUuid.toString()))
                .andExpect(jsonPath("$[0].tentativas").value(3));

        mockMvc.perform(post("/api/integracoes/google-calendar/ressincronizar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        AgendamentoCalendarOutbox recarregada = outboxRepository.findById(linha.getId()).orElseThrow();
        assertThat(recarregada.getTentativas()).isEqualTo(0);
        assertThat(recarregada.getProximaTentativaEm()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void nenhumTokenDeveAparecerNosLogsDoFluxoDeConexao() throws Exception {
        String token = autenticar("admin.gcallogs@teste.com", Perfil.ADMIN, "203.0.113.28");
        String state = extrairState(token);

        Logger rootLogger = (Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
        try {
            mockMvc.perform(get("/api/integracoes/google-calendar/callback")
                            .param("code", "codigo-simulado")
                            .param("state", state))
                    .andExpect(status().isFound());
        } finally {
            rootLogger.detachAppender(appender);
        }

        List<String> mensagens = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(mensagens).isNotEmpty();
        assertThat(mensagens).noneMatch(mensagem -> mensagem.contains("mock-refresh-token"));
    }

    private String extrairState(String token) throws Exception {
        String resposta = mockMvc.perform(get("/api/integracoes/google-calendar/conectar")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        String url = objectMapper.readTree(resposta).get("url").asText();
        List<String> valores = UriComponentsBuilder.fromUri(URI.create(url)).build().getQueryParams().get("state");
        return valores.get(0);
    }

    private UUID criarAgendamento(String token, UUID clienteUuid, UUID profissionalUuid, UUID servicoUuid,
            Instant inicio) throws Exception {
        String corpo = """
                {
                  "clienteUuid": "%s",
                  "profissionalUuid": "%s",
                  "servicoUuids": ["%s"],
                  "inicio": "%s",
                  "observacao": null
                }
                """.formatted(clienteUuid, profissionalUuid, servicoUuid, inicio.toString());

        String resposta = mockMvc.perform(post("/api/agendamentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
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
                  "email": "profissional.gcal@teste.com",
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

    private String autenticar(String email, Perfil perfil, String ipSimulado) throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuario de teste");
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPerfil(perfil);
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
