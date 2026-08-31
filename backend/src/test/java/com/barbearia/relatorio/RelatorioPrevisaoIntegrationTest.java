package com.barbearia.relatorio;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

@Transactional
class RelatorioPrevisaoIntegrationTest extends IntegrationTestBase {

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
    void previsaoDeCompromissosDeveReunirComissaoEstoqueBaixoEContasVencidas() throws Exception {
        String token = autenticar("admin.relatorioprev@teste.com", "198.51.107.3");

        UUID servicoUuid = criarServico(token, "Corte Previsao", 30, "80.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Previsao");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGradeDiaInteiroHoje(token, profissionalUuid);
        UUID clienteUuid = criarCliente(token, "Cliente Previsao", "(19) 99000-9201");

        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, servicoUuid);
        confirmar(token, agendamentoUuid);
        UUID comandaUuid = abrirComanda(token, agendamentoUuid);
        definirFormaPagamento(token, comandaUuid, "PIX");
        fecharComanda(token, comandaUuid);

        criarProduto(token, "Produto Estoque Baixo", "50.00", "20.00", 5);

        criarContaPagar(token, "Fornecedor de teste", "300.00", LocalDate.now(FUSO).minusDays(3));

        String resposta = mockMvc.perform(get("/api/relatorios/previsao-compromissos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode previsao = objectMapper.readTree(resposta);

        assertThat(previsao.get("comissaoTotalMes").asDouble()).isGreaterThan(0);
        assertThat(previsao.get("comissaoPorProfissional")).anySatisfy(item ->
                assertThat(item.get("nome").asText()).isEqualTo("Prof Previsao"));
        assertThat(previsao.get("produtosParaRepor")).anySatisfy(item ->
                assertThat(item.get("nome").asText()).isEqualTo("Produto Estoque Baixo"));
        assertThat(previsao.get("contasVencidas")).anySatisfy(item ->
                assertThat(item.get("descricao").asText()).isEqualTo("Fornecedor de teste"));
    }

    private UUID criarProduto(String token, String nome, String precoVenda, String precoCusto, int estoqueMinimo)
            throws Exception {
        String corpo = """
                {
                  "nome": "%s",
                  "precoVenda": %s,
                  "precoCusto": %s,
                  "estoqueMinimo": %d
                }
                """.formatted(nome, precoVenda, precoCusto, estoqueMinimo);

        String resposta = mockMvc.perform(post("/api/produtos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
    }

    private UUID criarContaPagar(String token, String descricao, String valor, LocalDate dataVencimento)
            throws Exception {
        String corpo = """
                {
                  "descricao": "%s",
                  "valor": %s,
                  "dataVencimento": "%s"
                }
                """.formatted(descricao, valor, dataVencimento);

        String resposta = mockMvc.perform(post("/api/contas-pagar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
    }

    private void confirmar(String token, UUID agendamentoUuid) throws Exception {
        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/confirmar")
                        .header("Authorization", "Bearer " + token))
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

    private void fecharComanda(String token, UUID comandaUuid) throws Exception {
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private UUID criarAgendamento(String token, UUID clienteUuid, UUID profissionalUuid, UUID servicoUuid)
            throws Exception {
        Instant inicio = ZonedDateTime.of(LocalDate.now(FUSO).plusDays(1), LocalTime.of(9, 0), FUSO).toInstant();
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
                  "email": "profissional.relatorioprev@teste.com",
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
        StringBuilder corpo = new StringBuilder("[");
        for (int diaSemana = 1; diaSemana <= 7; diaSemana++) {
            if (diaSemana > 1) {
                corpo.append(",");
            }
            corpo.append("{\"diaSemana\": ").append(diaSemana).append(", \"horaInicio\": \"00:00\", \"horaFim\": \"23:59\"}");
        }
        corpo.append("]");

        mockMvc.perform(put("/api/profissionais/" + profissionalUuid + "/grade-horaria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo.toString()))
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
