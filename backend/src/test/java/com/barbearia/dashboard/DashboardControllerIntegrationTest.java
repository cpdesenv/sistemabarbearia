package com.barbearia.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

@Transactional
class DashboardControllerIntegrationTest extends IntegrationTestBase {

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

    @Test
    void resumoDeveRefletirFaturamentoAtendimentosEOcupacaoDoDia() throws Exception {
        String token = autenticar("admin.dashboard1@teste.com", "198.51.105.1");

        UUID clienteUuid = criarCliente(token, "Cliente Dashboard", "(19) 99000-5001");
        UUID servicoUuid = criarServico(token, "Corte Dashboard", 30, "100.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Dashboard");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGradeDiaInteiroHoje(token, profissionalUuid);

        Instant inicio = ZonedDateTime.now(FUSO).plusMinutes(5).truncatedTo(ChronoUnit.MINUTES).toInstant();
        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, servicoUuid, inicio);
        confirmar(token, agendamentoUuid);
        UUID comandaUuid = abrirComanda(token, agendamentoUuid);
        definirFormaPagamento(token, comandaUuid, "PIX");
        fecharComanda(token, comandaUuid);

        MvcResult resultado = mockMvc.perform(get("/api/dashboard/resumo")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.faturamentoDia").value(100.00))
                .andExpect(jsonPath("$.cards.atendimentosDia").value(1))
                .andExpect(jsonPath("$.cards.ticketMedioDia").value(100.00))
                .andExpect(jsonPath("$.graficos.faturamentoUltimos12Meses.length()").value(12))
                .andReturn();

        JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());

        assertThat(corpo.get("cards").get("taxaOcupacaoHoje").asDouble()).isGreaterThan(0).isLessThan(100);
        assertThat(corpo.get("cards").get("faturamentoMes").asDouble()).isEqualTo(100.00);

        JsonNode ultimoPontoMensal = corpo.get("graficos").get("faturamentoUltimos12Meses").get(11);
        assertThat(ultimoPontoMensal.get("valor").asDouble()).isEqualTo(100.00);

        assertThat(corpo.get("graficos").get("servicosMaisVendidos")).anySatisfy(item ->
                assertThat(item.get("nome").asText()).isEqualTo("Corte Dashboard"));

        assertThat(corpo.get("graficos").get("atendimentosPorProfissional")).anySatisfy(item ->
                assertThat(item.get("nome").asText()).isEqualTo("Prof Dashboard"));

        assertThat(corpo.get("graficos").get("distribuicaoFormaPagamento")).anySatisfy(item ->
                assertThat(item.get("formaPagamento").asText()).isEqualTo("PIX"));

        assertThat(corpo.get("indicadoresSaude").get("clientesNovosMes").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void resumoDeveRefletirReceitaRecorrenteETaxaDeChurnDeAssinaturas() throws Exception {
        String token = autenticar("admin.dashboard2@teste.com", "198.51.105.2");

        UUID clienteUuid = criarCliente(token, "Cliente Assinante Dashboard", "(19) 99000-5002");
        UUID servicoUuid = criarServico(token, "Corte Clube Dashboard", 30, "60.00");
        UUID planoUuid = criarPlano(token, "Plano Dashboard", "80.00", 1, List.of(servicoUuid));
        UUID assinaturaUuid = assinar(token, clienteUuid, planoUuid);

        mockMvc.perform(get("/api/dashboard/resumo")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadoresAssinatura.receitaRecorrente").value(80.00))
                .andExpect(jsonPath("$.indicadoresAssinatura.taxaChurnMes").value(0.00));

        mockMvc.perform(post("/api/assinaturas/" + assinaturaUuid + "/cancelar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Teste dashboard\", \"dataEfeito\": \"" + LocalDate.now(FUSO) + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard/resumo")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadoresAssinatura.receitaRecorrente").value(0.00))
                .andExpect(jsonPath("$.indicadoresAssinatura.taxaChurnMes").value(100.00));
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
                  "email": "profissional.dashboard@teste.com",
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

    /**
     * Janela cobrindo o dia inteiro (00:00-23:59) no dia da semana de hoje, pra
     * que o agendamento de teste caiba independentemente do horario em que a
     * suite roda.
     */
    private void sincronizarGradeDiaInteiroHoje(String token, UUID profissionalUuid) throws Exception {
        int diaSemanaHoje = LocalDate.now(FUSO).getDayOfWeek().getValue();
        String corpo = "[{\"diaSemana\": " + diaSemanaHoje + ", \"horaInicio\": \"00:00\", \"horaFim\": \"23:59\"}]";

        mockMvc.perform(put("/api/profissionais/" + profissionalUuid + "/grade-horaria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());
    }

    private UUID criarPlano(String token, String nome, String precoMensal, int cortesIncluidos,
            List<UUID> servicosUuids) throws Exception {
        String servicosJson = servicosUuids.stream().map(uuid -> "\"" + uuid + "\"")
                .reduce((a, b) -> a + "," + b).orElse("");
        String corpo = """
                {
                  "nome": "%s",
                  "precoMensal": %s,
                  "cortesIncluidosPorCiclo": %d,
                  "percentualDescontoAdicional": 0,
                  "servicosInclusosUuids": [%s]
                }
                """.formatted(nome, precoMensal, cortesIncluidos, servicosJson);

        String resposta = mockMvc.perform(post("/api/planos-assinatura")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
    }

    private UUID assinar(String token, UUID clienteUuid, UUID planoUuid) throws Exception {
        String resposta = mockMvc.perform(post("/api/assinaturas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteUuid\": \"" + clienteUuid + "\", \"planoUuid\": \"" + planoUuid + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
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
