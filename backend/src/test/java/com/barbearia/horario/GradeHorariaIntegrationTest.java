package com.barbearia.horario;

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
class GradeHorariaIntegrationTest extends IntegrationTestBase {

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
        mockMvc.perform(get("/api/profissionais/" + UUID.randomUUID() + "/grade-horaria"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveSincronizarGradeComDuasJanelasNoMesmoDia() throws Exception {
        String token = autenticar("admin.grade1@teste.com", Perfil.ADMIN, "198.19.0.1");
        UUID profissionalUuid = criarProfissional(token);

        String corpo = """
                [
                  {"diaSemana": 1, "horaInicio": "09:00", "horaFim": "12:00"},
                  {"diaSemana": 1, "horaInicio": "13:00", "horaFim": "18:00"}
                ]
                """;

        mockMvc.perform(put("/api/profissionais/" + profissionalUuid + "/grade-horaria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/profissionais/" + profissionalUuid + "/grade-horaria")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        List<?> registros = auditoriaRepository.findAll().stream()
                .filter(a -> "GRADE_HORARIA_ATUALIZADA".equals(a.getOperacao()))
                .toList();
        assertThat(registros).isNotEmpty();
    }

    @Test
    void deveRecusarJanelasSobrepostasNoMesmoDia() throws Exception {
        String token = autenticar("admin.grade2@teste.com", Perfil.ADMIN, "198.19.0.2");
        UUID profissionalUuid = criarProfissional(token);

        String corpo = """
                [
                  {"diaSemana": 2, "horaInicio": "09:00", "horaFim": "13:00"},
                  {"diaSemana": 2, "horaInicio": "12:00", "horaFim": "18:00"}
                ]
                """;

        mockMvc.perform(put("/api/profissionais/" + profissionalUuid + "/grade-horaria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REGRA_DE_NEGOCIO"));
    }

    @Test
    void deveRecusarHoraFimAntesDeHoraInicio() throws Exception {
        String token = autenticar("admin.grade3@teste.com", Perfil.ADMIN, "198.19.0.3");
        UUID profissionalUuid = criarProfissional(token);

        String corpo = """
                [{"diaSemana": 3, "horaInicio": "18:00", "horaFim": "09:00"}]
                """;

        mockMvc.perform(put("/api/profissionais/" + profissionalUuid + "/grade-horaria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REGRA_DE_NEGOCIO"));
    }

    @Test
    void deveRecusarSincronizacaoComPerfilInsuficiente() throws Exception {
        String tokenAdmin = autenticar("admin.grade4@teste.com", Perfil.ADMIN, "198.19.0.4");
        UUID profissionalUuid = criarProfissional(tokenAdmin);
        String tokenBarbeiro = autenticar("barbeiro.grade4@teste.com", Perfil.BARBEIRO, "198.19.0.5");

        String corpo = """
                [{"diaSemana": 1, "horaInicio": "09:00", "horaFim": "12:00"}]
                """;

        mockMvc.perform(put("/api/profissionais/" + profissionalUuid + "/grade-horaria")
                        .header("Authorization", "Bearer " + tokenBarbeiro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isForbidden());
    }

    private UUID criarProfissional(String token) throws Exception {
        String corpo = """
                {
                  "nome": "Profissional Grade",
                  "email": "grade@teste.com",
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
