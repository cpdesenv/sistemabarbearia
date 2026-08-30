package com.barbearia.relatorio;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.financeiro.domain.Comanda;
import com.barbearia.financeiro.repository.ComandaRepository;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

@Transactional
class RelatorioFaturamentoIntegrationTest extends IntegrationTestBase {

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
    private ComandaRepository comandaRepository;

    @Test
    void relatorioDeveSomarHistoricoReprocessadoMaisComandaFechadaHoje() throws Exception {
        String token = autenticar("admin.relatorio1@teste.com", "198.51.106.1");

        UUID clienteUuid = criarCliente(token, "Cliente Relatorio", "(19) 99000-6001");
        UUID servicoUuid = criarServico(token, "Corte Relatorio", 5, "100.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Relatorio");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGradeDiaInteiroHoje(token, profissionalUuid);

        LocalDate cincoDiasAtras = LocalDate.now(FUSO).minusDays(5);
        UUID comandaAntigaUuid = criarEFecharComanda(token, clienteUuid, profissionalUuid, servicoUuid, "PIX");
        backdatarAgendamentoEComanda(comandaAntigaUuid, cincoDiasAtras);

        UUID comandaHojeUuid = criarEFecharComanda(token, clienteUuid, profissionalUuid, servicoUuid, "DINHEIRO");

        mockMvc.perform(post("/api/relatorios/reprocessar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataInicial\": \"" + cincoDiasAtras + "\", \"dataFinal\": \""
                                + cincoDiasAtras + "\"}"))
                .andExpect(status().isNoContent());

        String resposta = mockMvc.perform(get("/api/relatorios/faturamento")
                        .header("Authorization", "Bearer " + token)
                        .param("dataInicial", cincoDiasAtras.minusDays(1).toString())
                        .param("dataFinal", LocalDate.now(FUSO).toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode corpo = objectMapper.readTree(resposta);
        assertThat(corpo.get("valorTotal").asDouble()).isEqualTo(200.00);
        assertThat(corpo.get("quantidadeAtendimentos").asLong()).isEqualTo(2);
        assertThat(corpo.get("porServico")).anySatisfy(item -> {
            assertThat(item.get("nome").asText()).isEqualTo("Corte Relatorio");
            assertThat(item.get("quantidade").asLong()).isEqualTo(2);
        });
        assertThat(corpo.get("porFormaPagamento")).anySatisfy(item ->
                assertThat(item.get("formaPagamento").asText()).isEqualTo("PIX"));
        assertThat(corpo.get("porFormaPagamento")).anySatisfy(item ->
                assertThat(item.get("formaPagamento").asText()).isEqualTo("DINHEIRO"));
    }

    @Test
    void comparativoDeveContrastarMesAtualComMesAnterior() throws Exception {
        String token = autenticar("admin.relatorio2@teste.com", "198.51.106.2");

        UUID clienteUuid = criarCliente(token, "Cliente Comparativo", "(19) 99000-6002");
        UUID servicoUuid = criarServico(token, "Corte Comparativo", 5, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Comparativo");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGradeDiaInteiroHoje(token, profissionalUuid);

        YearMonth mesAtual = YearMonth.now(FUSO);
        LocalDate diaDoMesAnterior = mesAtual.minusMonths(1).atDay(1);

        UUID comandaMesAnteriorUuid = criarEFecharComanda(token, clienteUuid, profissionalUuid, servicoUuid, "PIX");
        backdatarAgendamentoEComanda(comandaMesAnteriorUuid, diaDoMesAnterior);
        mockMvc.perform(post("/api/relatorios/reprocessar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataInicial\": \"" + diaDoMesAnterior + "\", \"dataFinal\": \""
                                + diaDoMesAnterior + "\"}"))
                .andExpect(status().isNoContent());

        criarEFecharComanda(token, clienteUuid, profissionalUuid, servicoUuid, "PIX");

        String resposta = mockMvc.perform(get("/api/relatorios/faturamento/comparativo")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", mesAtual.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode corpo = objectMapper.readTree(resposta);
        assertThat(corpo.get("valorMesAtual").asDouble()).isEqualTo(50.00);
        assertThat(corpo.get("valorMesAnterior").asDouble()).isEqualTo(50.00);
        assertThat(corpo.get("variacaoPercentualMesAnterior").asDouble()).isEqualTo(0.00);
        assertThat(corpo.get("valorMesmoMesAnoAnterior").asDouble()).isEqualTo(0.00);
        assertThat(corpo.get("variacaoPercentualAnoAnterior").isNull()).isTrue();
    }

    /** Move o agendamento/comanda recem-criados para uma data no passado, direto no banco (a API nao permite agendar no passado). */
    private void backdatarAgendamentoEComanda(UUID comandaUuid, LocalDate novaData) {
        Comanda comanda = comandaRepository.findByUuidPublico(comandaUuid).orElseThrow();
        Agendamento agendamento = comanda.getAgendamento();

        ZonedDateTime novoInicioZoned = ZonedDateTime.of(novaData, agendamento.getInicio().atZone(FUSO).toLocalTime(),
                FUSO);
        long duracaoMinutos = Duration.between(agendamento.getInicio(), agendamento.getFim()).toMinutes();

        agendamento.setInicio(novoInicioZoned.toInstant());
        agendamento.setFim(novoInicioZoned.plusMinutes(duracaoMinutos).toInstant());
        agendamentoRepository.save(agendamento);

        comanda.setFechadaEm(novoInicioZoned.toInstant());
        comandaRepository.save(comanda);
    }

    private UUID criarEFecharComanda(String token, UUID clienteUuid, UUID profissionalUuid, UUID servicoUuid,
            String formaPagamento) throws Exception {
        Instant inicio = horarioSeguroHoje(5);
        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, servicoUuid, inicio);
        confirmar(token, agendamentoUuid);
        UUID comandaUuid = abrirComanda(token, agendamentoUuid);
        definirFormaPagamento(token, comandaUuid, formaPagamento);
        fecharComanda(token, comandaUuid);
        return comandaUuid;
    }

    /**
     * "Agora + 5 min" cruza a virada do dia se a suite rodar perto da meia-noite
     * local (falha real observada, nao teorica). Usa o mais tardar entre
     * "agora + 5 min" e o ultimo horario que ainda cabe o servico hoje.
     */
    private Instant horarioSeguroHoje(int duracaoServicoMinutos) {
        ZonedDateTime agora = ZonedDateTime.now(FUSO);
        ZonedDateTime desejado = agora.plusMinutes(5).truncatedTo(ChronoUnit.MINUTES);
        ZonedDateTime ultimoSlotHoje = agora.toLocalDate().atTime(23, 59).atZone(FUSO)
                .minusMinutes(duracaoServicoMinutos).truncatedTo(ChronoUnit.MINUTES);
        return (desejado.isAfter(ultimoSlotHoje) ? ultimoSlotHoje : desejado).toInstant();
    }

    private UUID abrirComanda(String token, UUID agendamentoUuid) throws Exception {
        String resposta = mockMvc.perform(post("/api/comandas/abrir-para-agendamento/" + agendamentoUuid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
    }

    private void definirFormaPagamento(String token, UUID comandaUuid, String formaPagamento) throws Exception {
        mockMvc.perform(put("/api/comandas/" + comandaUuid + "/forma-pagamento")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formaPagamento\": \"" + formaPagamento + "\"}"))
                .andExpect(status().isOk());
    }

    private void fecharComanda(String token, UUID comandaUuid) throws Exception {
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void confirmar(String token, UUID agendamentoUuid) throws Exception {
        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/confirmar")
                        .header("Authorization", "Bearer " + token))
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
                  "email": "profissional.relatorio@teste.com",
                  "telefone": "11900000000",
                  "corAgenda": "#3F51B5",
                  "comissaoPercentualPadrao": 20.00
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

    private void sincronizarGradeDiaInteiroHoje(String token, UUID profissionalUuid) throws Exception {
        int diaSemanaHoje = LocalDate.now(FUSO).getDayOfWeek().getValue();
        String corpo = "[{\"diaSemana\": " + diaSemanaHoje + ", \"horaInicio\": \"00:00\", \"horaFim\": \"23:59\"}]";

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
