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

/**
 * Cobre a integracao entre comanda e estoque (sub-entrega 5B): adicionar
 * produto a comanda, bloqueio por saldo insuficiente, baixa no fechamento e
 * devolucao no estorno.
 */
@Transactional
class ComandaProdutoIntegrationTest extends IntegrationTestBase {

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
    void venderProdutoComSaldoZeroDeveSerBloqueado() throws Exception {
        String token = autenticar("admin.saldozero@teste.com", "198.51.102.1");
        UUID produtoUuid = criarProduto(token, "Cera Modeladora", "40.00");
        UUID comandaUuid = abrirComandaComAgendamento(token, "Cliente Saldo Zero", "Prof Saldo Zero",
                "Corte Saldo Zero", "50.00");

        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/itens/produto")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoUuid\": \"" + produtoUuid + "\", \"quantidade\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REGRA_DE_NEGOCIO"));
    }

    @Test
    void venderDuasUnidadesComCincoEmEstoqueDeveDeixarSaldoTresEExtratoLigadoAComanda() throws Exception {
        String token = autenticar("admin.venda@teste.com", "198.51.102.2");
        UUID produtoUuid = criarProduto(token, "Pomada", "30.00");
        entradaEstoque(token, produtoUuid, 5);

        UUID comandaUuid = abrirComandaComAgendamento(token, "Cliente Venda", "Prof Venda", "Corte Venda", "50.00");

        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/itens/produto")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoUuid\": \"" + produtoUuid + "\", \"quantidade\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(2));

        definirFormaPagamento(token, comandaUuid, "DINHEIRO");
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorTotal").value(110.00));

        mockMvc.perform(get("/api/produtos/" + produtoUuid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.estoqueAtual").value(3));

        mockMvc.perform(get("/api/produtos/" + produtoUuid + "/movimentos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.content[0].tipo").value("SAIDA"))
                .andExpect(jsonPath("$.content[0].quantidade").value(-2))
                .andExpect(jsonPath("$.content[0].comandaId").isNotEmpty());
    }

    @Test
    void estornarComandaComProdutoDeveDevolverEstoque() throws Exception {
        String token = autenticar("admin.estornoproduto@teste.com", "198.51.102.3");
        UUID produtoUuid = criarProduto(token, "Oleo de Barba", "25.00");
        entradaEstoque(token, produtoUuid, 5);

        UUID comandaUuid = abrirComandaComAgendamento(token, "Cliente Estorno Produto", "Prof Estorno Produto",
                "Corte Estorno Produto", "50.00");
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/itens/produto")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoUuid\": \"" + produtoUuid + "\", \"quantidade\": 2}"))
                .andExpect(status().isOk());

        definirFormaPagamento(token, comandaUuid, "PIX");
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/estornar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Cliente devolveu o produto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESTORNADA"));

        mockMvc.perform(get("/api/produtos/" + produtoUuid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.estoqueAtual").value(5));

        mockMvc.perform(get("/api/produtos/" + produtoUuid + "/movimentos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.content[0].tipo").value("DEVOLUCAO"))
                .andExpect(jsonPath("$.content[0].quantidade").value(2))
                .andExpect(jsonPath("$.content[0].comandaId").isNotEmpty());
    }

    @Test
    void itemDeProdutoNaoDeveGerarComissao() throws Exception {
        String token = autenticar("admin.semcomissao@teste.com", "198.51.102.4");
        UUID produtoUuid = criarProduto(token, "Shampoo", "20.00");
        entradaEstoque(token, produtoUuid, 3);

        UUID comandaUuid = abrirComandaComAgendamento(token, "Cliente Sem Comissao", "Prof Sem Comissao",
                "Corte Sem Comissao", "50.00");
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/itens/produto")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoUuid\": \"" + produtoUuid + "\", \"quantidade\": 1}"))
                .andExpect(status().isOk());

        MvcResult resultado = mockMvc.perform(get("/api/comandas/" + comandaUuid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode comanda = objectMapper.readTree(resultado.getResponse().getContentAsString());
        JsonNode itemProduto = comanda.get("itens").get(1);
        assertThat(itemProduto.get("tipo").asText()).isEqualTo("PRODUTO");
        assertThat(itemProduto.get("comissaoValor").isNull()).isTrue();
    }

    private UUID abrirComandaComAgendamento(String token, String nomeCliente, String nomeProfissional,
            String nomeServico, String preco) throws Exception {
        UUID clienteUuid = criarCliente(token, nomeCliente, "(19) 99000-" + Math.abs(nomeCliente.hashCode() % 9000));
        UUID servicoUuid = criarServico(token, nomeServico, 45, preco);
        UUID profissionalUuid = criarProfissional(token, nomeProfissional);
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        LocalDate data = proximaSegunda();
        Instant inicio = ZonedDateTime.of(data, LocalTime.of(9, 0), FUSO).toInstant();
        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, List.of(servicoUuid), inicio);
        confirmar(token, agendamentoUuid);
        return abrirComanda(token, agendamentoUuid);
    }

    private UUID criarProduto(String token, String nome, String precoVenda) throws Exception {
        String corpo = """
                {
                  "nome": "%s",
                  "descricao": "Produto de teste",
                  "categoria": "Estetica",
                  "unidade": "UN",
                  "precoVenda": %s,
                  "precoCusto": 10.00,
                  "estoqueMinimo": 2
                }
                """.formatted(nome, precoVenda);

        String resposta = mockMvc.perform(post("/api/produtos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
    }

    private void entradaEstoque(String token, UUID produtoUuid, int quantidade) throws Exception {
        mockMvc.perform(post("/api/produtos/" + produtoUuid + "/entrada-estoque")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\": " + quantidade + ", \"custoUnitario\": 10.00}"))
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
