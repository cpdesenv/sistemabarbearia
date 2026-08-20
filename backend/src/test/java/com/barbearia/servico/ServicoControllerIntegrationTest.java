package com.barbearia.servico;

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
class ServicoControllerIntegrationTest extends IntegrationTestBase {

    private static final String SENHA = "SenhaForte123!";
    private static final String CORPO_VALIDO = """
            {
              "nome": "Corte Masculino",
              "descricao": "Corte tradicional com maquina e tesoura",
              "categoria": "Corte",
              "preco": 50.00,
              "duracaoMinutos": 45
            }
            """;

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
        mockMvc.perform(get("/api/servicos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveCriarServicoComoAdmin() throws Exception {
        String token = autenticar("admin.criar@teste.com", Perfil.ADMIN, "192.0.2.10");

        mockMvc.perform(post("/api/servicos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Corte Masculino"))
                .andExpect(jsonPath("$.preco").value(50.00))
                .andExpect(jsonPath("$.duracaoMinutos").value(45))
                .andExpect(jsonPath("$.ativo").value(true));

        List<?> registros = auditoriaRepository.findAll().stream()
                .filter(a -> "SERVICO_CRIADO".equals(a.getOperacao()))
                .toList();
        assertThat(registros).isNotEmpty();
    }

    @Test
    void deveRecusarCriacaoComPerfilInsuficiente() throws Exception {
        String token = autenticar("barbeiro.criar@teste.com", Perfil.BARBEIRO, "192.0.2.11");

        mockMvc.perform(post("/api/servicos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRecusarPrecoNegativo() throws Exception {
        String token = autenticar("admin.preco@teste.com", Perfil.ADMIN, "192.0.2.12");
        String corpo = CORPO_VALIDO.replace("50.00", "-10.00");

        mockMvc.perform(post("/api/servicos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("preco"));
    }

    @Test
    void deveRecusarDuracaoZero() throws Exception {
        String token = autenticar("admin.duracao@teste.com", Perfil.ADMIN, "192.0.2.13");
        String corpo = CORPO_VALIDO.replace("45", "0");

        mockMvc.perform(post("/api/servicos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("duracaoMinutos"));
    }

    @Test
    void deveListarComFiltroDeNomeEPaginacao() throws Exception {
        String token = autenticar("admin.listar@teste.com", Perfil.ADMIN, "192.0.2.14");

        criarServico(token, "Corte Masculino", "Corte", "50.00", 45);
        criarServico(token, "Barba Completa", "Barba", "35.00", 30);

        mockMvc.perform(get("/api/servicos")
                        .header("Authorization", "Bearer " + token)
                        .param("nome", "barba"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Barba Completa"));
    }

    @Test
    void deveAtualizarServico() throws Exception {
        String token = autenticar("admin.atualizar@teste.com", Perfil.ADMIN, "192.0.2.15");
        UUID uuid = criarServico(token, "Corte Simples", "Corte", "40.00", 30);

        String corpoAtualizado = """
                {
                  "nome": "Corte Premium",
                  "descricao": "Corte com acabamento na navalha",
                  "categoria": "Corte",
                  "preco": 60.00,
                  "duracaoMinutos": 50
                }
                """;

        mockMvc.perform(put("/api/servicos/" + uuid)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAtualizado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Corte Premium"))
                .andExpect(jsonPath("$.preco").value(60.00));
    }

    @Test
    void deveDesativarEReativarServico() throws Exception {
        String token = autenticar("admin.status@teste.com", Perfil.ADMIN, "192.0.2.16");
        UUID uuid = criarServico(token, "Sobrancelha", "Estetica", "20.00", 15);

        mockMvc.perform(patch("/api/servicos/" + uuid + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(patch("/api/servicos/" + uuid + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));

        List<String> operacoes = auditoriaRepository.findAll().stream()
                .map(a -> a.getOperacao())
                .toList();
        assertThat(operacoes).contains("SERVICO_DESATIVADO", "SERVICO_ATIVADO");
    }

    @Test
    void deveRetornar404ParaUuidInexistente() throws Exception {
        String token = autenticar("admin.notfound@teste.com", Perfil.ADMIN, "192.0.2.17");

        mockMvc.perform(get("/api/servicos/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private UUID criarServico(String token, String nome, String categoria, String preco, int duracaoMinutos)
            throws Exception {
        String corpo = """
                {
                  "nome": "%s",
                  "descricao": "Descricao de teste",
                  "categoria": "%s",
                  "preco": %s,
                  "duracaoMinutos": %d
                }
                """.formatted(nome, categoria, preco, duracaoMinutos);

        String resposta = mockMvc.perform(post("/api/servicos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
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
