package com.barbearia.profissional;

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
class ProfissionalControllerIntegrationTest extends IntegrationTestBase {

    private static final String SENHA = "SenhaForte123!";
    private static final String CORPO_VALIDO = """
            {
              "nome": "Carlos Barbeiro",
              "email": "carlos@teste.com",
              "telefone": "11988887777",
              "corAgenda": "#3F51B5",
              "comissaoPercentualPadrao": 40.00
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
        mockMvc.perform(get("/api/profissionais"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveCriarProfissionalComoAdmin() throws Exception {
        String token = autenticar("admin.criarprof@teste.com", Perfil.ADMIN, "198.18.0.1");

        mockMvc.perform(post("/api/profissionais")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Carlos Barbeiro"))
                .andExpect(jsonPath("$.corAgenda").value("#3F51B5"))
                .andExpect(jsonPath("$.ativo").value(true));

        List<?> registros = auditoriaRepository.findAll().stream()
                .filter(a -> "PROFISSIONAL_CRIADO".equals(a.getOperacao()))
                .toList();
        assertThat(registros).isNotEmpty();
    }

    @Test
    void deveRecusarCriacaoComPerfilInsuficiente() throws Exception {
        String token = autenticar("barbeiro.criarprof@teste.com", Perfil.BARBEIRO, "198.18.0.2");

        mockMvc.perform(post("/api/profissionais")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRecusarCorInvalida() throws Exception {
        String token = autenticar("admin.cor@teste.com", Perfil.ADMIN, "198.18.0.3");
        String corpo = CORPO_VALIDO.replace("#3F51B5", "azul");

        mockMvc.perform(post("/api/profissionais")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("corAgenda"));
    }

    @Test
    void deveRecusarComissaoAcimaDeCem() throws Exception {
        String token = autenticar("admin.comissao@teste.com", Perfil.ADMIN, "198.18.0.4");
        String corpo = CORPO_VALIDO.replace("40.00", "150.00");

        mockMvc.perform(post("/api/profissionais")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("comissaoPercentualPadrao"));
    }

    @Test
    void deveAtualizarEDesativarProfissional() throws Exception {
        String token = autenticar("admin.atualizarprof@teste.com", Perfil.ADMIN, "198.18.0.5");
        UUID uuid = criarProfissional(token);

        String corpoAtualizado = CORPO_VALIDO.replace("Carlos Barbeiro", "Carlos Silva");
        mockMvc.perform(put("/api/profissionais/" + uuid)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAtualizado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Carlos Silva"));

        mockMvc.perform(patch("/api/profissionais/" + uuid + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void deveSincronizarServicosVinculadosComComissaoEfetiva() throws Exception {
        String token = autenticar("admin.vinculo@teste.com", Perfil.ADMIN, "198.18.0.6");
        UUID profissionalUuid = criarProfissional(token);
        UUID servicoComComissaoPropria = criarServico(token, "Corte Premium", "80.00");
        UUID servicoSemComissaoPropria = criarServico(token, "Barba", "35.00");

        String corpoVinculos = """
                [
                  {"servicoUuid": "%s", "comissaoPercentual": 55.00},
                  {"servicoUuid": "%s", "comissaoPercentual": null}
                ]
                """.formatted(servicoComComissaoPropria, servicoSemComissaoPropria);

        mockMvc.perform(put("/api/profissionais/" + profissionalUuid + "/servicos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoVinculos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/profissionais/" + profissionalUuid + "/servicos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.servicoUuid=='" + servicoComComissaoPropria + "')].comissaoEfetiva")
                        .value(55.0))
                .andExpect(jsonPath("$[?(@.servicoUuid=='" + servicoSemComissaoPropria + "')].comissaoEfetiva")
                        .value(40.0));

        List<?> registros = auditoriaRepository.findAll().stream()
                .filter(a -> "PROFISSIONAL_SERVICOS_ATUALIZADOS".equals(a.getOperacao()))
                .toList();
        assertThat(registros).isNotEmpty();
    }

    @Test
    void deveRecusarServicoDuplicadoNaSincronizacao() throws Exception {
        String token = autenticar("admin.duplicado@teste.com", Perfil.ADMIN, "198.18.0.7");
        UUID profissionalUuid = criarProfissional(token);
        UUID servicoUuid = criarServico(token, "Corte Simples", "40.00");

        String corpoVinculos = """
                [
                  {"servicoUuid": "%s", "comissaoPercentual": 10.00},
                  {"servicoUuid": "%s", "comissaoPercentual": 20.00}
                ]
                """.formatted(servicoUuid, servicoUuid);

        mockMvc.perform(put("/api/profissionais/" + profissionalUuid + "/servicos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoVinculos))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REGRA_DE_NEGOCIO"));
    }

    private UUID criarProfissional(String token) throws Exception {
        String resposta = mockMvc.perform(post("/api/profissionais")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
    }

    private UUID criarServico(String token, String nome, String preco) throws Exception {
        String corpo = """
                {"nome": "%s", "categoria": "Corte", "preco": %s, "duracaoMinutos": 30}
                """.formatted(nome, preco);

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
