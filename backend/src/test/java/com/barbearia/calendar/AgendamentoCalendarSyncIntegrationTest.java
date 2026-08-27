package com.barbearia.calendar;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.calendar.domain.AgendamentoCalendarOutbox;
import com.barbearia.calendar.domain.StatusOutbox;
import com.barbearia.calendar.domain.TipoOperacaoOutbox;
import com.barbearia.calendar.repository.AgendamentoCalendarOutboxRepository;
import com.barbearia.calendar.service.CalendarOutboxWorker;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

/**
 * Cobre o outbox de sincronizacao com o Google Calendar (Fase 8) de ponta a
 * ponta, com o {@code MockCalendarGateway} (padrao em teste, sem nenhuma
 * credencial Google) — confirmar enfileira CRIAR, remarcar enfileira
 * ATUALIZAR, cancelar enfileira REMOVER, e o worker processa cada um.
 */
@Transactional
class AgendamentoCalendarSyncIntegrationTest extends IntegrationTestBase {

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
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private AgendamentoCalendarOutboxRepository outboxRepository;
    @Autowired
    private CalendarOutboxWorker calendarOutboxWorker;

    @Test
    void confirmarRemarcarECancelarDevemPassarPeloOutboxAteOFim() throws Exception {
        String token = autenticar("admin.calendar@teste.com", "203.0.113.10");
        UUID clienteUuid = criarCliente(token, "Cliente Calendar", "(19) 90000-2222");
        UUID servicoUuid = criarServico(token, "Corte Calendar", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Calendar");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        LocalDate proximaSegunda = ZonedDateTime.now(FUSO).toLocalDate()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Instant inicio = ZonedDateTime.of(proximaSegunda, LocalTime.of(9, 0), FUSO).toInstant();
        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, servicoUuid, inicio);

        // Antes de confirmar: nenhuma pendencia de sincronizacao.
        assertThat(outboxRepository.findByStatusNot(StatusOutbox.CONCLUIDO)).isEmpty();

        confirmar(token, agendamentoUuid);
        AgendamentoCalendarOutbox linhaCriar = unicaLinhaPendente(agendamentoUuid);
        assertThat(linhaCriar.getTipoOperacao()).isEqualTo(TipoOperacaoOutbox.CRIAR);

        calendarOutboxWorker.processarPendencias();
        Agendamento agendamento = agendamentoRepository.findByUuidPublico(agendamentoUuid).orElseThrow();
        assertThat(agendamento.getGoogleEventId()).startsWith("mock-evento-");
        assertThat(agendamento.getGoogleCalendarId()).isNotBlank();
        assertThat(outboxRepository.findById(linhaCriar.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusOutbox.CONCLUIDO);
        String googleEventIdOriginal = agendamento.getGoogleEventId();

        Instant novoInicio = ZonedDateTime.of(proximaSegunda, LocalTime.of(11, 0), FUSO).toInstant();
        remarcar(token, agendamentoUuid, clienteUuid, profissionalUuid, servicoUuid, novoInicio);
        AgendamentoCalendarOutbox linhaAtualizar = unicaLinhaPendente(agendamentoUuid);
        assertThat(linhaAtualizar.getTipoOperacao()).isEqualTo(TipoOperacaoOutbox.ATUALIZAR);

        calendarOutboxWorker.processarPendencias();
        agendamento = agendamentoRepository.findByUuidPublico(agendamentoUuid).orElseThrow();
        assertThat(agendamento.getGoogleEventId()).isEqualTo(googleEventIdOriginal);
        assertThat(outboxRepository.findById(linhaAtualizar.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusOutbox.CONCLUIDO);

        cancelar(token, agendamentoUuid);
        AgendamentoCalendarOutbox linhaRemover = unicaLinhaPendente(agendamentoUuid);
        assertThat(linhaRemover.getTipoOperacao()).isEqualTo(TipoOperacaoOutbox.REMOVER);

        calendarOutboxWorker.processarPendencias();
        agendamento = agendamentoRepository.findByUuidPublico(agendamentoUuid).orElseThrow();
        assertThat(agendamento.getGoogleEventId()).isNull();
        assertThat(agendamento.getGoogleCalendarId()).isNull();
        assertThat(outboxRepository.findById(linhaRemover.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusOutbox.CONCLUIDO);
    }

    @Test
    void cancelarAgendamentoNuncaConfirmadoNaoDeveGerarPendenciaDeSincronizacao() throws Exception {
        String token = autenticar("admin.calendarcancel@teste.com", "203.0.113.11");
        UUID clienteUuid = criarCliente(token, "Cliente Calendar Cancel", "(19) 90000-3333");
        UUID servicoUuid = criarServico(token, "Corte Calendar Cancel", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Calendar Cancel");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        LocalDate proximaSegunda = ZonedDateTime.now(FUSO).toLocalDate()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Instant inicio = ZonedDateTime.of(proximaSegunda, LocalTime.of(14, 0), FUSO).toInstant();
        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, servicoUuid, inicio);

        cancelar(token, agendamentoUuid);

        Long agendamentoId = agendamentoRepository.findByUuidPublico(agendamentoUuid).orElseThrow().getId();
        boolean temPendencia = outboxRepository.findByStatusNot(StatusOutbox.CONCLUIDO).stream()
                .anyMatch(linha -> linha.getAgendamento().getId().equals(agendamentoId));
        assertThat(temPendencia).isFalse();
    }

    private AgendamentoCalendarOutbox unicaLinhaPendente(UUID agendamentoUuid) {
        Long agendamentoId = agendamentoRepository.findByUuidPublico(agendamentoUuid).orElseThrow().getId();
        List<AgendamentoCalendarOutbox> pendentes = outboxRepository.findByStatusNot(StatusOutbox.CONCLUIDO).stream()
                .filter(linha -> linha.getAgendamento().getId().equals(agendamentoId))
                .toList();
        assertThat(pendentes).hasSize(1);
        return pendentes.get(0);
    }

    private void confirmar(String token, UUID agendamentoUuid) throws Exception {
        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/confirmar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void remarcar(String token, UUID agendamentoUuid, UUID clienteUuid, UUID profissionalUuid,
            UUID servicoUuid, Instant novoInicio) throws Exception {
        String corpo = """
                {
                  "clienteUuid": "%s",
                  "profissionalUuid": "%s",
                  "servicoUuids": ["%s"],
                  "inicio": "%s",
                  "observacao": null
                }
                """.formatted(clienteUuid, profissionalUuid, servicoUuid, novoInicio.toString());

        mockMvc.perform(put("/api/agendamentos/" + agendamentoUuid)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());
    }

    private void cancelar(String token, UUID agendamentoUuid) throws Exception {
        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/cancelar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Teste automatizado\"}"))
                .andExpect(status().isOk());
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
                  "email": "profissional.calendar@teste.com",
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
