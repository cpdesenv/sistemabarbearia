package com.barbearia.financeiro;

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
import org.springframework.transaction.annotation.Transactional;

@Transactional
class DespesaContaControllerIntegrationTest extends IntegrationTestBase {

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
    void despesaDeveReduzirCaixaEmMaosNoFluxoDeCaixa() throws Exception {
        String token = autenticar("admin.despesa@teste.com", "198.51.102.1");

        mockMvc.perform(post("/api/despesas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\": \"" + LocalDate.now(FUSO) + "\", \"categoria\": \"Aluguel\", "
                                + "\"valor\": 250.00, \"descricao\": \"Aluguel de agosto\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor").value(250.00));

        mockMvc.perform(get("/api/financeiro/fluxo-caixa")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caixaEmMaos").value(-250.00))
                .andExpect(jsonPath("$.fluxoCaixa").value(-250.00));
    }

    @Test
    void contaAReceberDeveAparecerNoFluxoEDeixarDeAparecerAoSerRecebida() throws Exception {
        String token = autenticar("admin.receber@teste.com", "198.51.102.2");
        UUID clienteUuid = criarCliente(token, "Cliente Devedor", "(19) 99000-2001");

        String resposta = mockMvc.perform(post("/api/contas-receber")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteUuid\": \"" + clienteUuid + "\", \"descricao\": \"Corte fiado\", "
                                + "\"valor\": 80.00, \"dataVencimento\": \"" + LocalDate.now(FUSO).plusDays(10) + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();
        UUID contaUuid = UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());

        mockMvc.perform(get("/api/financeiro/fluxo-caixa")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.contasAReceberEsperadas").value(80.00))
                .andExpect(jsonPath("$.fluxoCaixa").value(80.00));

        mockMvc.perform(post("/api/contas-receber/" + contaUuid + "/receber")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEBIDA"));

        mockMvc.perform(get("/api/financeiro/fluxo-caixa")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.contasAReceberEsperadas").value(0))
                .andExpect(jsonPath("$.fluxoCaixa").value(0));
    }

    @Test
    void contaAPagarSoEntraNoFluxoQuandoVencidaESaiAoSerPaga() throws Exception {
        String token = autenticar("admin.pagar@teste.com", "198.51.102.3");

        String respostaFutura = mockMvc.perform(post("/api/contas-pagar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\": \"Fornecedor futuro\", \"valor\": 120.00, "
                                + "\"dataVencimento\": \"" + LocalDate.now(FUSO).plusDays(15) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        objectMapper.readTree(respostaFutura).get("uuid").asText();

        mockMvc.perform(get("/api/financeiro/fluxo-caixa")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.contasAPagarVencidas").value(0));

        String respostaVencida = mockMvc.perform(post("/api/contas-pagar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\": \"Fornecedor vencido\", \"valor\": 45.00, "
                                + "\"dataVencimento\": \"" + LocalDate.now(FUSO).minusDays(3) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID contaVencidaUuid = UUID.fromString(objectMapper.readTree(respostaVencida).get("uuid").asText());

        mockMvc.perform(get("/api/financeiro/fluxo-caixa")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.contasAPagarVencidas").value(45.00))
                .andExpect(jsonPath("$.fluxoCaixa").value(-45.00));

        mockMvc.perform(post("/api/contas-pagar/" + contaVencidaUuid + "/pagar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGA"));

        mockMvc.perform(get("/api/financeiro/fluxo-caixa")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.contasAPagarVencidas").value(0));
    }

    @Test
    void comandaFechadaDeveEntrarNoCaixaEmMaos() throws Exception {
        String token = autenticar("admin.comandacaixa@teste.com", "198.51.102.4");
        UUID clienteUuid = criarCliente(token, "Cliente Fluxo", "(19) 99000-2002");
        UUID servicoUuid = criarServico(token, "Corte Fluxo", 45, "65.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Fluxo", "30.00");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        LocalDate data = proximaSegunda();
        Instant inicio = ZonedDateTime.of(data, LocalTime.of(9, 0), FUSO).toInstant();
        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, List.of(servicoUuid), inicio);
        confirmar(token, agendamentoUuid);
        UUID comandaUuid = abrirComanda(token, agendamentoUuid);
        definirFormaPagamento(token, comandaUuid, "DINHEIRO");
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/financeiro/fluxo-caixa")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.caixaEmMaos").value(65.00))
                .andExpect(jsonPath("$.fluxoCaixa").value(65.00));
    }

    @Test
    void permissoesDeLancamentoERecebimento() throws Exception {
        String tokenAdmin = autenticar("admin.permcontas@teste.com", "198.51.102.5");
        UUID clienteUuid = criarCliente(tokenAdmin, "Cliente Permissao Conta", "(19) 99000-2003");

        String tokenRecepcao = autenticar("recepcao.permcontas@teste.com", Perfil.RECEPCAO, "198.51.102.6");

        String resposta = mockMvc.perform(post("/api/contas-receber")
                        .header("Authorization", "Bearer " + tokenRecepcao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteUuid\": \"" + clienteUuid + "\", \"valor\": 30.00, "
                                + "\"dataVencimento\": \"" + LocalDate.now(FUSO).plusDays(5) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID contaUuid = UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());

        mockMvc.perform(post("/api/contas-receber/" + contaUuid + "/receber")
                        .header("Authorization", "Bearer " + tokenRecepcao))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/despesas")
                        .header("Authorization", "Bearer " + tokenRecepcao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\": \"" + LocalDate.now(FUSO) + "\", \"valor\": 10.00}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/contas-pagar")
                        .header("Authorization", "Bearer " + tokenRecepcao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\": \"Teste\", \"valor\": 10.00, \"dataVencimento\": \""
                                + LocalDate.now(FUSO).plusDays(5) + "\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/contas-receber/" + contaUuid + "/receber")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
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

    private void confirmar(String token, UUID agendamentoUuid) throws Exception {
        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/confirmar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
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
                """.formatted(clienteUuid, profissionalUuid, servicosJson, inicio.toString());
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

    private UUID criarProfissional(String token, String nome, String comissaoPercentualPadrao) throws Exception {
        String corpo = """
                {
                  "nome": "%s",
                  "email": "profissional@teste.com",
                  "telefone": "11900000000",
                  "corAgenda": "#3F51B5",
                  "comissaoPercentualPadrao": %s
                }
                """.formatted(nome, comissaoPercentualPadrao);

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
