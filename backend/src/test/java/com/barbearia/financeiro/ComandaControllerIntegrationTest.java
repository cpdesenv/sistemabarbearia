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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.shared.auditoria.AuditoriaRepository;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

@Transactional
class ComandaControllerIntegrationTest extends IntegrationTestBase {

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
    private AuditoriaRepository auditoriaRepository;

    @Test
    void abrirComandaDeveTransicionarAgendamentoEIniciarComItensDoAgendamento() throws Exception {
        String token = autenticar("admin.abrir@teste.com", "198.51.101.1");
        UUID agendamentoUuid = montarAgendamentoConfirmado(token, "Cliente Abrir", "Prof Abrir", "Corte Abrir",
                "100.00", 30);

        MvcResult resultado = mockMvc.perform(post("/api/comandas/abrir-para-agendamento/" + agendamentoUuid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTA"))
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.subtotal").value(100.00))
                .andReturn();

        JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
        assertThat(corpo.get("agendamentoUuid").asText()).isEqualTo(agendamentoUuid.toString());

        mockMvc.perform(get("/api/agendamentos/" + agendamentoUuid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"));
    }

    @Test
    void abrirComandaDuasVezesDeveSerIdempotente() throws Exception {
        String token = autenticar("admin.idempotente@teste.com", "198.51.101.2");
        UUID agendamentoUuid = montarAgendamentoConfirmado(token, "Cliente Idempotente", "Prof Idempotente",
                "Corte Idempotente", "80.00", 30);

        UUID comanda1 = abrirComanda(token, agendamentoUuid);
        UUID comanda2 = abrirComanda(token, agendamentoUuid);

        assertThat(comanda2).isEqualTo(comanda1);
    }

    @Test
    void fecharComandaDeveEntrarNoCaixaETransicionarAgendamentoParaFinalizado() throws Exception {
        String token = autenticar("admin.fechar@teste.com", "198.51.101.3");
        UUID agendamentoUuid = montarAgendamentoConfirmado(token, "Cliente Fechar", "Prof Fechar", "Corte Fechar",
                "70.00", 30);
        UUID comandaUuid = abrirComanda(token, agendamentoUuid);

        definirFormaPagamento(token, comandaUuid, "DINHEIRO");

        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADA"))
                .andExpect(jsonPath("$.valorTotal").value(70.00));

        mockMvc.perform(get("/api/agendamentos/" + agendamentoUuid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("FINALIZADO"));

        String hoje = LocalDate.now(FUSO).toString();
        mockMvc.perform(get("/api/caixa").param("data", hoje)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGeral").value(70.00))
                .andExpect(jsonPath("$.porFormaPagamento[0].formaPagamento").value("DINHEIRO"))
                .andExpect(jsonPath("$.porFormaPagamento[0].total").value(70.00))
                .andExpect(jsonPath("$.porProfissional[0].totalFaturado").value(70.00))
                .andExpect(jsonPath("$.porProfissional[0].totalComissao").value(21.00));
    }

    @Test
    void descontoDeveSerRateadoERecalcularComissaoSobreOValorLiquido() throws Exception {
        String token = autenticar("admin.desconto@teste.com", "198.51.101.4");
        UUID clienteUuid = criarCliente(token, "Cliente Desconto", "(19) 99000-1001");
        UUID servico1 = criarServico(token, "Corte Desconto", 45, "100.00");
        UUID servico2 = criarServico(token, "Barba Desconto", 30, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Desconto", "30.00");
        vincularServicos(token, profissionalUuid, List.of(servico1, servico2));
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        LocalDate data = proximaSegunda();
        Instant inicio = ZonedDateTime.of(data, LocalTime.of(9, 0), FUSO).toInstant();
        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, List.of(servico1, servico2),
                inicio);
        confirmar(token, agendamentoUuid);
        UUID comandaUuid = abrirComanda(token, agendamentoUuid);

        mockMvc.perform(put("/api/comandas/" + comandaUuid + "/desconto")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\": 15.00, \"motivo\": \"Cliente fidelidade\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorTotal").value(135.00))
                .andExpect(jsonPath("$.comissaoTotal").value(40.50));

        definirFormaPagamento(token, comandaUuid, "PIX");
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorTotal").value(135.00));
    }

    @Test
    void descontoSemMotivoDeveSerRecusado() throws Exception {
        String token = autenticar("admin.descontosemmotivo@teste.com", "198.51.101.5");
        UUID agendamentoUuid = montarAgendamentoConfirmado(token, "Cliente Sem Motivo", "Prof Sem Motivo",
                "Corte Sem Motivo", "50.00", 30);
        UUID comandaUuid = abrirComanda(token, agendamentoUuid);

        mockMvc.perform(put("/api/comandas/" + comandaUuid + "/desconto")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\": 5.00, \"motivo\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REGRA_DE_NEGOCIO"));
    }

    @Test
    void editarComandaFechadaDeveSerBloqueado() throws Exception {
        String token = autenticar("admin.editarfechada@teste.com", "198.51.101.6");
        UUID agendamentoUuid = montarAgendamentoConfirmado(token, "Cliente Editar Fechada", "Prof Editar Fechada",
                "Corte Editar Fechada", "60.00", 30);
        UUID comandaUuid = abrirComanda(token, agendamentoUuid);
        definirFormaPagamento(token, comandaUuid, "CARTAO_CREDITO");
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/comandas/" + comandaUuid + "/desconto")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\": 5.00, \"motivo\": \"Tarde demais\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REGRA_DE_NEGOCIO"));

        mockMvc.perform(put("/api/comandas/" + comandaUuid + "/forma-pagamento")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formaPagamento\": \"PIX\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fecharSemFormaDePagamentoOuSemItensDeveSerBloqueado() throws Exception {
        String token = autenticar("admin.fecharsemitem@teste.com", "198.51.101.7");
        UUID agendamentoUuid = montarAgendamentoConfirmado(token, "Cliente Sem Item", "Prof Sem Item",
                "Corte Sem Item", "40.00", 30);
        UUID comandaUuid = abrirComanda(token, agendamentoUuid);

        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REGRA_DE_NEGOCIO"));

        JsonNode comanda = objectMapper.readTree(mockMvc.perform(get("/api/comandas/" + comandaUuid)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString());
        UUID itemUuid = UUID.fromString(comanda.get("itens").get(0).get("uuid").asText());

        mockMvc.perform(delete("/api/comandas/" + comandaUuid + "/itens/" + itemUuid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0));

        definirFormaPagamento(token, comandaUuid, "DINHEIRO");
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REGRA_DE_NEGOCIO"));
    }

    @Test
    void estornarDeveGerarAuditoriaESairDoCaixa() throws Exception {
        String token = autenticar("admin.estornar@teste.com", "198.51.101.8");
        UUID agendamentoUuid = montarAgendamentoConfirmado(token, "Cliente Estorno", "Prof Estorno",
                "Corte Estorno", "90.00", 30);
        UUID comandaUuid = abrirComanda(token, agendamentoUuid);
        definirFormaPagamento(token, comandaUuid, "DINHEIRO");
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long auditoriasAntes = auditoriaRepository.count();

        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/estornar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Cobranca duplicada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESTORNADA"));

        assertThat(auditoriaRepository.count()).isEqualTo(auditoriasAntes + 1);
        assertThat(auditoriaRepository.findAll().stream()
                .anyMatch(a -> "COMANDA_ESTORNADA".equals(a.getOperacao()) && a.getUsuarioId() != null))
                .isTrue();

        String hoje = LocalDate.now(FUSO).toString();
        mockMvc.perform(get("/api/caixa").param("data", hoje)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGeral").value(0));
    }

    @Test
    void barbeiroNaoConsegueEstornarMasConsegueFecharEAdicionarItem() throws Exception {
        String tokenAdmin = autenticar("admin.permissoes@teste.com", "198.51.101.9");
        UUID clienteUuid = criarCliente(tokenAdmin, "Cliente Permissoes", "(19) 99000-1002");
        UUID servicoUuid = criarServico(tokenAdmin, "Corte Permissoes", 45, "55.00");
        UUID servicoExtra = criarServico(tokenAdmin, "Sobrancelha Permissoes", 15, "20.00");
        UUID profissionalUuid = criarProfissional(tokenAdmin, "Prof Permissoes", "30.00");
        vincularServicos(tokenAdmin, profissionalUuid, List.of(servicoUuid, servicoExtra));
        sincronizarGrade(tokenAdmin, profissionalUuid, 1, "09:00", "18:00");

        LocalDate data = proximaSegunda();
        Instant inicio = ZonedDateTime.of(data, LocalTime.of(9, 0), FUSO).toInstant();
        UUID agendamentoUuid = criarAgendamento(tokenAdmin, clienteUuid, profissionalUuid, List.of(servicoUuid),
                inicio);
        confirmar(tokenAdmin, agendamentoUuid);

        String tokenBarbeiro = autenticar("barbeiro.permissoes@teste.com", Perfil.BARBEIRO, "198.51.101.10");
        UUID comandaUuid = abrirComanda(tokenBarbeiro, agendamentoUuid);

        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/itens")
                        .header("Authorization", "Bearer " + tokenBarbeiro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"servicoUuid\": \"" + servicoExtra + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(2));

        definirFormaPagamento(tokenBarbeiro, comandaUuid, "DINHEIRO");

        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + tokenBarbeiro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADA"));

        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/estornar")
                        .header("Authorization", "Bearer " + tokenBarbeiro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Tentativa de estorno\"}"))
                .andExpect(status().isForbidden());
    }

    private UUID montarAgendamentoConfirmado(String token, String nomeCliente, String nomeProfissional,
            String nomeServico, String preco, int comissaoPercentual) throws Exception {
        UUID clienteUuid = criarCliente(token, nomeCliente, "(19) 99000-" + Math.abs(nomeCliente.hashCode() % 9000));
        UUID servicoUuid = criarServico(token, nomeServico, 45, preco);
        UUID profissionalUuid = criarProfissional(token, nomeProfissional, comissaoPercentual + ".00");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        LocalDate data = proximaSegunda();
        Instant inicio = ZonedDateTime.of(data, LocalTime.of(9, 0), FUSO).toInstant();
        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, List.of(servicoUuid), inicio);
        confirmar(token, agendamentoUuid);
        return agendamentoUuid;
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
        vincularServicos(token, profissionalUuid, List.of(servicoUuid));
    }

    /**
     * O endpoint de vinculo e' uma sincronizacao completa (substitui a lista
     * inteira), nao uma adicao — por isso os servicos precisam ser passados
     * todos juntos numa unica chamada quando o profissional atende mais de um.
     */
    private void vincularServicos(String token, UUID profissionalUuid, List<UUID> servicoUuids) throws Exception {
        String corpo = servicoUuids.stream()
                .map(uuid -> "{\"servicoUuid\": \"" + uuid + "\", \"comissaoPercentual\": null}")
                .reduce((a, b) -> a + "," + b)
                .map(itens -> "[" + itens + "]")
                .orElse("[]");

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
