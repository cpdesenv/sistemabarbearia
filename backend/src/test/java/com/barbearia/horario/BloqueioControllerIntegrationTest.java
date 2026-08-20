package com.barbearia.horario;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class BloqueioControllerIntegrationTest extends IntegrationTestBase {

    private static final String SENHA = "SenhaForte123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void deveExigirAutenticacaoParaListar() throws Exception {
        mockMvc.perform(get("/api/bloqueios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveCriarBloqueioGlobal() throws Exception {
        String token = autenticar("admin.bloqueio1@teste.com", Perfil.ADMIN, "198.20.0.1");

        String corpo = """
                {
                  "inicio": "2026-12-25T00:00:00Z",
                  "fim": "2026-12-26T00:00:00Z",
                  "motivo": "Feriado de Natal"
                }
                """;

        mockMvc.perform(post("/api/bloqueios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profissionalUuid").doesNotExist())
                .andExpect(jsonPath("$.motivo").value("Feriado de Natal"));
    }

    @Test
    void deveAceitarBloqueioSobrepostoAGradeHorariaEPrevalecerSobreEla() throws Exception {
        String token = autenticar("admin.bloqueio2@teste.com", Perfil.ADMIN, "198.20.0.2");
        UUID profissionalUuid = criarProfissional(token);

        mockMvc.perform(put("/api/profissionais/" + profissionalUuid + "/grade-horaria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"diaSemana\": 1, \"horaInicio\": \"09:00\", \"horaFim\": \"18:00\"}]"))
                .andExpect(status().isOk());

        String corpoBloqueio = """
                {
                  "profissionalUuid": "%s",
                  "inicio": "2026-08-24T13:00:00Z",
                  "fim": "2026-08-24T15:00:00Z",
                  "motivo": "Consulta medica"
                }
                """.formatted(profissionalUuid);

        mockMvc.perform(post("/api/bloqueios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoBloqueio))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profissionalUuid").value(profissionalUuid.toString()));
    }

    @Test
    void deveRecusarFimAntesDoInicio() throws Exception {
        String token = autenticar("admin.bloqueio3@teste.com", Perfil.ADMIN, "198.20.0.3");

        String corpo = """
                {
                  "inicio": "2026-08-24T15:00:00Z",
                  "fim": "2026-08-24T13:00:00Z",
                  "motivo": "Invalido"
                }
                """;

        mockMvc.perform(post("/api/bloqueios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REGRA_DE_NEGOCIO"));
    }

    @Test
    void deveRecusarCriacaoComPerfilInsuficiente() throws Exception {
        String token = autenticar("barbeiro.bloqueio4@teste.com", Perfil.BARBEIRO, "198.20.0.4");

        String corpo = """
                {"inicio": "2026-08-24T13:00:00Z", "fim": "2026-08-24T15:00:00Z", "motivo": "Teste"}
                """;

        mockMvc.perform(post("/api/bloqueios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRemoverBloqueio() throws Exception {
        String token = autenticar("admin.bloqueio5@teste.com", Perfil.ADMIN, "198.20.0.5");

        String corpo = """
                {"inicio": "2026-09-01T00:00:00Z", "fim": "2026-09-02T00:00:00Z", "motivo": "Teste de remocao"}
                """;

        String resposta = mockMvc.perform(post("/api/bloqueios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID uuid = UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());

        mockMvc.perform(delete("/api/bloqueios/" + uuid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private UUID criarProfissional(String token) throws Exception {
        String corpo = """
                {
                  "nome": "Profissional Bloqueio",
                  "email": "bloqueio@teste.com",
                  "telefone": "11900000000",
                  "corAgenda": "#3F51B5",
                  "comissaoPercentualPadrao": 30.00
                }
                """;

        String resposta = mockMvc.perform(post("/api/profissionais")
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
