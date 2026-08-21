package com.barbearia.agenda;

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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.agenda.domain.AgendamentoServico;
import com.barbearia.agenda.domain.OrigemAgendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.cliente.domain.Cliente;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.repository.ProfissionalRepository;
import com.barbearia.servico.domain.Servico;
import com.barbearia.servico.repository.ServicoRepository;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

@Transactional
class AgendamentoControllerIntegrationTest extends IntegrationTestBase {

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
    private ClienteRepository clienteRepository;
    @Autowired
    private ProfissionalRepository profissionalRepository;
    @Autowired
    private ServicoRepository servicoRepository;

    @Test
    void deveExigirAutenticacaoParaListar() throws Exception {
        mockMvc.perform(get("/api/agendamentos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornarApenasSlotsQueComportamDuracaoDoServico() throws Exception {
        String token = autenticar("admin.slots@teste.com", "198.51.100.1");
        LocalDate data = proximaSegunda();

        UUID servicoUuid = criarServico(token, "Corte 45min", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Slots");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "10:00");

        MvcResult resultado = mockMvc.perform(get("/api/agenda/disponibilidade")
                        .header("Authorization", "Bearer " + token)
                        .param("data", data.toString())
                        .param("servicoUuids", servicoUuid.toString())
                        .param("profissionalUuid", profissionalUuid.toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode slots = objectMapper.readTree(resultado.getResponse().getContentAsString());
        assertThat(slots).hasSize(2);
        assertThat(horaLocal(slots.get(0).get("inicio").asText())).isEqualTo(LocalTime.of(9, 0));
        assertThat(horaLocal(slots.get(1).get("inicio").asText())).isEqualTo(LocalTime.of(9, 15));
        for (JsonNode slot : slots) {
            Instant inicio = Instant.parse(slot.get("inicio").asText());
            Instant fim = Instant.parse(slot.get("fim").asText());
            assertThat(fim.getEpochSecond() - inicio.getEpochSecond()).isEqualTo(45 * 60);
        }
    }

    @Test
    void bloqueioDeAlmocoDeveRemoverOsSlotsCorrespondentes() throws Exception {
        String token = autenticar("admin.bloqueio@teste.com", "198.51.100.2");
        LocalDate data = proximaSegunda();

        UUID servicoUuid = criarServico(token, "Corte Bloqueio", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Bloqueio");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        Instant inicioBloqueio = ZonedDateTime.of(data, LocalTime.of(12, 0), FUSO).toInstant();
        Instant fimBloqueio = ZonedDateTime.of(data, LocalTime.of(13, 0), FUSO).toInstant();
        criarBloqueio(token, profissionalUuid, inicioBloqueio, fimBloqueio);

        MvcResult resultado = mockMvc.perform(get("/api/agenda/disponibilidade")
                        .header("Authorization", "Bearer " + token)
                        .param("data", data.toString())
                        .param("servicoUuids", servicoUuid.toString())
                        .param("profissionalUuid", profissionalUuid.toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode slots = objectMapper.readTree(resultado.getResponse().getContentAsString());
        for (JsonNode slot : slots) {
            LocalTime horaInicio = horaLocal(slot.get("inicio").asText());
            boolean dentroDoBloqueio = !horaInicio.isBefore(LocalTime.of(12, 0))
                    && horaInicio.isBefore(LocalTime.of(13, 0));
            assertThat(dentroDoBloqueio).isFalse();
        }
        assertThat(slots).anyMatch(slot -> horaLocal(slot.get("inicio").asText()).equals(LocalTime.of(11, 0)));
    }

    @Test
    void deveCriarAgendamentoERespeitarOFuso() throws Exception {
        String token = autenticar("admin.criar@teste.com", "198.51.100.3");
        LocalDate data = proximaSegunda();

        UUID clienteUuid = criarCliente(token, "Cliente Agenda", "(19) 99000-0001");
        UUID servicoUuid = criarServico(token, "Corte Fuso", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Fuso");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        Instant inicio = ZonedDateTime.of(data, LocalTime.of(9, 0), FUSO).toInstant();

        MvcResult resultado = mockMvc.perform(post("/api/agendamentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAgendamento(clienteUuid, profissionalUuid, List.of(servicoUuid), inicio)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGENDADO"))
                .andExpect(jsonPath("$.valorTotal").value(50.00))
                .andReturn();

        JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
        Instant inicioRetornado = Instant.parse(corpo.get("inicio").asText());
        assertThat(inicioRetornado).isEqualTo(inicio);
        assertThat(ZonedDateTime.ofInstant(inicioRetornado, FUSO).toLocalTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(inicioRetornado.atZone(ZoneId.of("UTC")).toLocalTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void deveRecusarAgendamentoForaDoHorarioDeFuncionamento() throws Exception {
        String token = autenticar("admin.forahorario@teste.com", "198.51.100.4");
        LocalDate data = proximaSegunda();

        UUID clienteUuid = criarCliente(token, "Cliente Fora Horario", "(19) 99000-0002");
        UUID servicoUuid = criarServico(token, "Corte Fora Horario", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Fora Horario");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        Instant inicio = ZonedDateTime.of(data, LocalTime.of(20, 0), FUSO).toInstant();

        mockMvc.perform(post("/api/agendamentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAgendamento(clienteUuid, profissionalUuid, List.of(servicoUuid), inicio)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REGRA_DE_NEGOCIO"));
    }

    @Test
    void deveRecusarAgendamentoNoPassado() throws Exception {
        String token = autenticar("admin.passado@teste.com", "198.51.100.5");

        UUID clienteUuid = criarCliente(token, "Cliente Passado", "(19) 99000-0003");
        UUID servicoUuid = criarServico(token, "Corte Passado", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Passado");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        Instant inicio = Instant.now().minusSeconds(3600);

        mockMvc.perform(post("/api/agendamentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAgendamento(clienteUuid, profissionalUuid, List.of(servicoUuid), inicio)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REGRA_DE_NEGOCIO"));
    }

    @Test
    void cancelamentoDeveLiberarOSlotImediatamente() throws Exception {
        String token = autenticar("admin.cancelar@teste.com", "198.51.100.6");
        LocalDate data = proximaSegunda();

        UUID clienteUuid = criarCliente(token, "Cliente Cancela", "(19) 99000-0004");
        UUID servicoUuid = criarServico(token, "Corte Cancela", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Cancela");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "10:00");

        Instant inicio = ZonedDateTime.of(data, LocalTime.of(9, 0), FUSO).toInstant();
        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, List.of(servicoUuid), inicio);

        assertThat(disponibilidadeContemHorario(token, data, servicoUuid, profissionalUuid, LocalTime.of(9, 0)))
                .isFalse();

        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/cancelar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Cliente desistiu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        assertThat(disponibilidadeContemHorario(token, data, servicoUuid, profissionalUuid, LocalTime.of(9, 0)))
                .isTrue();
    }

    @Test
    void devePercorrerOFluxoCompletoDeStatus() throws Exception {
        String token = autenticar("admin.fluxo@teste.com", "198.51.100.7");
        LocalDate data = proximaSegunda();

        UUID clienteUuid = criarCliente(token, "Cliente Fluxo", "(19) 99000-0005");
        UUID servicoUuid = criarServico(token, "Corte Fluxo", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Fluxo");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        Instant inicio = ZonedDateTime.of(data, LocalTime.of(9, 0), FUSO).toInstant();
        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, List.of(servicoUuid), inicio);

        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/finalizar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/confirmar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADO"));

        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/iniciar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"));

        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/finalizar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZADO"));
    }

    @Test
    void deveRemarcarAgendamentoArrastandoParaOutroHorario() throws Exception {
        String token = autenticar("admin.remarcar@teste.com", "198.51.100.8");
        LocalDate data = proximaSegunda();

        UUID clienteUuid = criarCliente(token, "Cliente Remarca", "(19) 99000-0006");
        UUID servicoUuid = criarServico(token, "Corte Remarca", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Remarca");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        Instant inicioOriginal = ZonedDateTime.of(data, LocalTime.of(9, 0), FUSO).toInstant();
        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, List.of(servicoUuid),
                inicioOriginal);

        Instant novoInicio = ZonedDateTime.of(data, LocalTime.of(14, 0), FUSO).toInstant();
        mockMvc.perform(put("/api/agendamentos/" + agendamentoUuid)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAgendamento(clienteUuid, profissionalUuid, List.of(servicoUuid), novoInicio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value(novoInicio.toString()));

        assertThat(disponibilidadeContemHorario(token, data, servicoUuid, profissionalUuid, LocalTime.of(9, 0)))
                .isTrue();
    }

    @Test
    void deveRecusarCriacaoComPerfilInsuficiente() throws Exception {
        String tokenAdmin = autenticar("admin.permissao@teste.com", "198.51.100.9");
        LocalDate data = proximaSegunda();

        UUID clienteUuid = criarCliente(tokenAdmin, "Cliente Permissao", "(19) 99000-0007");
        UUID servicoUuid = criarServico(tokenAdmin, "Corte Permissao", 45, "50.00");
        UUID profissionalUuid = criarProfissional(tokenAdmin, "Prof Permissao");
        vincularServico(tokenAdmin, profissionalUuid, servicoUuid);
        sincronizarGrade(tokenAdmin, profissionalUuid, 1, "09:00", "18:00");

        String tokenBarbeiro = autenticar("barbeiro.permissao@teste.com", Perfil.BARBEIRO, "198.51.100.10");
        Instant inicio = ZonedDateTime.of(data, LocalTime.of(9, 0), FUSO).toInstant();

        mockMvc.perform(post("/api/agendamentos")
                        .header("Authorization", "Bearer " + tokenBarbeiro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAgendamento(clienteUuid, profissionalUuid, List.of(servicoUuid), inicio)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRetornar404ParaUuidInexistente() throws Exception {
        String token = autenticar("admin.notfound@teste.com", "198.51.100.11");

        mockMvc.perform(get("/api/agendamentos/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRecusarAgendamentoQueSeSobrepoeAOutroJaExistente() throws Exception {
        String token = autenticar("admin.sobreposicao@teste.com", "198.51.100.13");
        LocalDate data = proximaSegunda();

        UUID clienteUuid = criarCliente(token, "Cliente Sobreposicao", "(19) 99000-0009");
        UUID servicoUuid = criarServico(token, "Corte Sobreposicao", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Sobreposicao");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        Instant inicio = ZonedDateTime.of(data, LocalTime.of(9, 0), FUSO).toInstant();
        criarAgendamento(token, clienteUuid, profissionalUuid, List.of(servicoUuid), inicio);

        Instant inicioSobreposto = ZonedDateTime.of(data, LocalTime.of(9, 15), FUSO).toInstant();
        mockMvc.perform(post("/api/agendamentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAgendamento(clienteUuid, profissionalUuid, List.of(servicoUuid),
                                inicioSobreposto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REGRA_DE_NEGOCIO"));
    }

    /**
     * Testa diretamente a constraint de exclusao do Postgres (nao o
     * pre-check em Java, que e' apenas uma conveniencia de UX e nao seria
     * suficiente sozinho sob concorrencia real — ver o comentario em
     * {@link com.barbearia.agenda.domain.Agendamento}). Duas transacoes
     * independentes tentam inserir, sem nenhuma leitura previa entre elas,
     * o mesmo par (profissional, horario); a segunda a commitar tem que ser
     * rejeitada pelo banco, nao importa a ordem em que as threads rodem.
     * Roda fora da transacao do teste para que o setup (feito via API,
     * portanto ja commitado por conta propria) fique visivel as duas
     * conexoes independentes abertas pelas threads.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void constraintDeExclusaoDeveAceitarApenasUmaInsercaoConcorrenteParaOMesmoHorario() throws Exception {
        String token = autenticar("admin.constraint@teste.com", "198.51.100.14");
        LocalDate data = proximaSegunda();

        UUID clienteUuid = criarCliente(token, "Cliente Constraint", "(19) 99000-0010");
        UUID servicoUuid = criarServico(token, "Corte Constraint", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Constraint");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        Cliente cliente = clienteRepository.findByUuidPublico(clienteUuid).orElseThrow();
        Profissional profissional = profissionalRepository.findByUuidPublico(profissionalUuid).orElseThrow();
        Servico servico = servicoRepository.findByUuidPublico(servicoUuid).orElseThrow();
        Instant inicio = ZonedDateTime.of(data, LocalTime.of(9, 0), FUSO).toInstant();
        Instant fim = inicio.plusSeconds(servico.getDuracaoMinutos() * 60L);

        Callable<Boolean> inserir = () -> {
            Agendamento agendamento = new Agendamento();
            agendamento.setCliente(cliente);
            agendamento.setProfissional(profissional);
            agendamento.setInicio(inicio);
            agendamento.setFim(fim);
            agendamento.setOrigem(OrigemAgendamento.MANUAL);
            agendamento.setValorTotal(servico.getPreco());
            agendamento.adicionarServico(
                    new AgendamentoServico(servico, servico.getDuracaoMinutos(), servico.getPreco()));
            try {
                agendamentoRepository.saveAndFlush(agendamento);
                return true;
            } catch (DataIntegrityViolationException ex) {
                return false;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> resultados = executor.invokeAll(List.of(inserir, inserir));
            long sucessos = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get()) {
                    sucessos++;
                }
            }
            assertThat(sucessos).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    private boolean disponibilidadeContemHorario(String token, LocalDate data, UUID servicoUuid,
            UUID profissionalUuid, LocalTime horario) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/agenda/disponibilidade")
                        .header("Authorization", "Bearer " + token)
                        .param("data", data.toString())
                        .param("servicoUuids", servicoUuid.toString())
                        .param("profissionalUuid", profissionalUuid.toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode slots = objectMapper.readTree(resultado.getResponse().getContentAsString());
        for (JsonNode slot : slots) {
            if (horaLocal(slot.get("inicio").asText()).equals(horario)) {
                return true;
            }
        }
        return false;
    }

    private LocalTime horaLocal(String instanteIso) {
        return ZonedDateTime.ofInstant(Instant.parse(instanteIso), FUSO).toLocalTime();
    }

    private LocalDate proximaSegunda() {
        return ZonedDateTime.now(FUSO).toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private String corpoAgendamento(UUID clienteUuid, UUID profissionalUuid, List<UUID> servicoUuids,
            Instant inicio) {
        String servicosJson = servicoUuids.stream().map(uuid -> "\"" + uuid + "\"")
                .reduce((a, b) -> a + "," + b).orElse("");
        return """
                {
                  "clienteUuid": "%s",
                  "profissionalUuid": "%s",
                  "servicoUuids": [%s],
                  "inicio": "%s",
                  "observacao": null
                }
                """.formatted(clienteUuid, profissionalUuid, servicosJson,
                DateTimeFormatter.ISO_INSTANT.format(inicio));
    }

    private UUID criarAgendamento(String token, UUID clienteUuid, UUID profissionalUuid, List<UUID> servicoUuids,
            Instant inicio) throws Exception {
        String resposta = mockMvc.perform(post("/api/agendamentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAgendamento(clienteUuid, profissionalUuid, servicoUuids, inicio)))
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

    private void criarBloqueio(String token, UUID profissionalUuid, Instant inicio, Instant fim) throws Exception {
        String corpo = """
                {
                  "profissionalUuid": "%s",
                  "inicio": "%s",
                  "fim": "%s",
                  "motivo": "Almoco"
                }
                """.formatted(profissionalUuid, DateTimeFormatter.ISO_INSTANT.format(inicio),
                DateTimeFormatter.ISO_INSTANT.format(fim));

        mockMvc.perform(post("/api/bloqueios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated());
    }

    private String autenticar(String email, String ipSimulado) throws Exception {
        return autenticar(email, Perfil.ADMIN, ipSimulado);
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
