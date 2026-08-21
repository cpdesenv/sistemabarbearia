package com.barbearia.produto;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class ProdutoControllerIntegrationTest extends IntegrationTestBase {

    private static final String SENHA = "SenhaForte123!";

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
    void deveExigirAutenticacaoParaListar() throws Exception {
        mockMvc.perform(get("/api/produtos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveCriarProdutoComoAdmin() throws Exception {
        String token = autenticar("admin.criar@teste.com", Perfil.ADMIN, "192.0.3.10");

        mockMvc.perform(post("/api/produtos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoProduto("Pomada Modeladora", "Estetica", "45.00", "20.00", 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Pomada Modeladora"))
                .andExpect(jsonPath("$.precoVenda").value(45.00))
                .andExpect(jsonPath("$.precoCusto").value(20.00))
                .andExpect(jsonPath("$.estoqueMinimo").value(5))
                .andExpect(jsonPath("$.estoqueAtual").value(0))
                .andExpect(jsonPath("$.unidade").value("UN"))
                .andExpect(jsonPath("$.ativo").value(true));

        assertThat(auditoriaRepository.findAll().stream().anyMatch(a -> "PRODUTO_CRIADO".equals(a.getOperacao())))
                .isTrue();
    }

    @Test
    void deveRecusarCriacaoComPerfilInsuficiente() throws Exception {
        String token = autenticar("barbeiro.criar@teste.com", Perfil.BARBEIRO, "192.0.3.11");

        mockMvc.perform(post("/api/produtos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoProduto("Shampoo", "Estetica", "30.00", "15.00", 3)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveAtualizarEDesativarProduto() throws Exception {
        String token = autenticar("admin.status@teste.com", Perfil.ADMIN, "192.0.3.12");
        UUID uuid = criarProduto(token, "Cera Capilar", "Estetica", "25.00", "10.00", 2);

        mockMvc.perform(put("/api/produtos/" + uuid)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoProduto("Cera Capilar Premium", "Estetica", "35.00", "15.00", 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Cera Capilar Premium"))
                .andExpect(jsonPath("$.precoVenda").value(35.00));

        mockMvc.perform(patch("/api/produtos/" + uuid + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        List<String> operacoes = auditoriaRepository.findAll().stream().map(a -> a.getOperacao()).toList();
        assertThat(operacoes).contains("PRODUTO_ATUALIZADO", "PRODUTO_DESATIVADO");
    }

    @Test
    void entradaDeveAumentarEstoqueERegistrarMovimento() throws Exception {
        String token = autenticar("admin.entrada@teste.com", Perfil.ADMIN, "192.0.3.13");
        UUID uuid = criarProduto(token, "Oleo para Barba", "Estetica", "40.00", "18.00", 3);

        mockMvc.perform(post("/api/produtos/" + uuid + "/entrada-estoque")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\": 10, \"custoUnitario\": 18.00, \"motivo\": \"Compra fornecedor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estoqueAtual").value(10));

        mockMvc.perform(get("/api/produtos/" + uuid + "/movimentos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.content[0].quantidade").value(10));

        assertThat(auditoriaRepository.findAll().stream().anyMatch(a -> "ESTOQUE_ENTRADA".equals(a.getOperacao())))
                .isTrue();
    }

    @Test
    void ajusteSemMotivoDeveSerRecusado() throws Exception {
        String token = autenticar("admin.ajustesemmotivo@teste.com", Perfil.ADMIN, "192.0.3.14");
        UUID uuid = criarProduto(token, "Talco", "Estetica", "15.00", "5.00", 2);

        mockMvc.perform(post("/api/produtos/" + uuid + "/ajuste-estoque")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novaQuantidadeContada\": 5, \"motivo\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ajusteComMotivoDeveCorrigirSaldoERegistrarMovimento() throws Exception {
        String token = autenticar("admin.ajuste@teste.com", Perfil.ADMIN, "192.0.3.15");
        UUID uuid = criarProduto(token, "Gel Fixador", "Estetica", "22.00", "9.00", 2);

        mockMvc.perform(post("/api/produtos/" + uuid + "/entrada-estoque")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\": 8, \"custoUnitario\": 9.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/produtos/" + uuid + "/ajuste-estoque")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novaQuantidadeContada\": 5, \"motivo\": \"Contagem de inventario\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estoqueAtual").value(5));

        mockMvc.perform(get("/api/produtos/" + uuid + "/movimentos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.content[0].quantidade").value(-3))
                .andExpect(jsonPath("$.content[0].motivo").value("Contagem de inventario"));

        assertThat(auditoriaRepository.findAll().stream().anyMatch(a -> "ESTOQUE_AJUSTADO".equals(a.getOperacao())))
                .isTrue();
    }

    @Test
    void produtoAbaixoDoEstoqueMinimoDeveAparecerNoAlerta() throws Exception {
        String token = autenticar("admin.alerta@teste.com", Perfil.ADMIN, "192.0.3.16");
        UUID abaixoUuid = criarProduto(token, "Navalha Descartavel", "Ferramentas", "8.00", "3.00", 10);
        UUID acimaUuid = criarProduto(token, "Tesoura", "Ferramentas", "120.00", "60.00", 1);

        mockMvc.perform(post("/api/produtos/" + abaixoUuid + "/entrada-estoque")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\": 2}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/produtos/" + acimaUuid + "/entrada-estoque")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\": 5}"))
                .andExpect(status().isOk());

        String resposta = mockMvc.perform(get("/api/produtos/alertas-estoque-minimo")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> nomesEmAlerta = objectMapper.readTree(resposta).findValuesAsText("nome");
        assertThat(nomesEmAlerta).contains("Navalha Descartavel");
        assertThat(nomesEmAlerta).doesNotContain("Tesoura");
    }

    @Test
    void deveRetornar404ParaUuidInexistente() throws Exception {
        String token = autenticar("admin.notfound@teste.com", Perfil.ADMIN, "192.0.3.17");

        mockMvc.perform(get("/api/produtos/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private String corpoProduto(String nome, String categoria, String precoVenda, String precoCusto,
            int estoqueMinimo) {
        return """
                {
                  "nome": "%s",
                  "descricao": "Descricao de teste",
                  "categoria": "%s",
                  "unidade": "UN",
                  "precoVenda": %s,
                  "precoCusto": %s,
                  "estoqueMinimo": %d
                }
                """.formatted(nome, categoria, precoVenda, precoCusto, estoqueMinimo);
    }

    private UUID criarProduto(String token, String nome, String categoria, String precoVenda, String precoCusto,
            int estoqueMinimo) throws Exception {
        String resposta = mockMvc.perform(post("/api/produtos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoProduto(nome, categoria, precoVenda, precoCusto, estoqueMinimo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
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
