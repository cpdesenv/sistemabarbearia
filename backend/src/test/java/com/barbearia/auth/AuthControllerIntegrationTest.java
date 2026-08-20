package com.barbearia.auth;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.shared.auditoria.AuditoriaRepository;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

@Transactional
class AuthControllerIntegrationTest extends IntegrationTestBase {

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
    void deveFazerLoginComCredenciaisValidas() throws Exception {
        criarUsuario("barbeiro.login@teste.com", Perfil.BARBEIRO);

        mockMvc.perform(login("barbeiro.login@teste.com", SENHA, "198.51.100.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.usuario.email").value("barbeiro.login@teste.com"))
                .andExpect(jsonPath("$.usuario.perfil").value("BARBEIRO"));
    }

    @Test
    void deveRecusarLoginComSenhaInvalida() throws Exception {
        criarUsuario("barbeiro.senhaerrada@teste.com", Perfil.BARBEIRO);

        mockMvc.perform(login("barbeiro.senhaerrada@teste.com", "senha-completamente-errada", "198.51.100.2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("CREDENCIAIS_INVALIDAS"));

        List<?> registros = auditoriaRepository.findAll().stream()
                .filter(a -> "LOGIN_FALHA".equals(a.getOperacao()))
                .toList();
        assertThat(registros).isNotEmpty();
    }

    @Test
    void deveRecusarLoginComEmailInexistente() throws Exception {
        mockMvc.perform(login("nao.existe@teste.com", SENHA, "198.51.100.3"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    void deveRenovarTokensERevogarOAntigoNaRotacao() throws Exception {
        criarUsuario("barbeiro.refresh@teste.com", Perfil.BARBEIRO);

        String corpoLogin = mockMvc.perform(login("barbeiro.refresh@teste.com", SENHA, "198.51.100.4"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshTokenOriginal = campo(corpoLogin, "refreshToken");

        String corpoRefresh = mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Forwarded-For", "198.51.100.4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshTokenOriginal + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String refreshTokenNovo = campo(corpoRefresh, "refreshToken");

        assertThat(refreshTokenNovo).isNotEqualTo(refreshTokenOriginal);

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Forwarded-For", "198.51.100.4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshTokenOriginal + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRecusarRefreshComTokenInvalido() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Forwarded-For", "198.51.100.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"token-que-nao-existe\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutDeveInvalidarORefreshToken() throws Exception {
        criarUsuario("barbeiro.logout@teste.com", Perfil.BARBEIRO);

        String corpoLogin = mockMvc.perform(login("barbeiro.logout@teste.com", SENHA, "198.51.100.6"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = campo(corpoLogin, "refreshToken");

        mockMvc.perform(post("/api/auth/logout")
                        .header("X-Forwarded-For", "198.51.100.6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Forwarded-For", "198.51.100.6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveNegarAcessoARotaProtegidaSemToken() throws Exception {
        mockMvc.perform(get("/api/teste/autenticado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("NAO_AUTENTICADO"));
    }

    @Test
    void deveNegarAcessoComPerfilInsuficiente() throws Exception {
        criarUsuario("barbeiro.semacesso@teste.com", Perfil.BARBEIRO);

        String corpoLogin = mockMvc.perform(login("barbeiro.semacesso@teste.com", SENHA, "198.51.100.7"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = campo(corpoLogin, "accessToken");

        mockMvc.perform(get("/api/teste/admin").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("ACESSO_NEGADO"));
    }

    @Test
    void devePermitirAcessoComPerfilSuficiente() throws Exception {
        criarUsuario("admin.comacesso@teste.com", Perfil.ADMIN);

        String corpoLogin = mockMvc.perform(login("admin.comacesso@teste.com", SENHA, "198.51.100.8"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = campo(corpoLogin, "accessToken");

        mockMvc.perform(get("/api/teste/admin").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void deveAplicarRateLimitingNoLogin() throws Exception {
        String ip = "203.0.113.50";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login("nao.importa@teste.com", "senha-errada", ip))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login("nao.importa@teste.com", "senha-errada", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value("LIMITE_DE_TENTATIVAS_EXCEDIDO"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email,
            String senha, String ipSimulado) throws Exception {
        return post("/api/auth/login")
                .header("X-Forwarded-For", ipSimulado)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, senha)));
    }

    private String campo(String jsonBody, String nomeCampo) throws Exception {
        JsonNode node = objectMapper.readTree(jsonBody);
        return node.get(nomeCampo).asText();
    }

    private void criarUsuario(String email, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuario de teste");
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        usuarioRepository.save(usuario);
    }
}
