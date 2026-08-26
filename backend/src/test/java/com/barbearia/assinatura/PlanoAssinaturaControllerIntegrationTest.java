package com.barbearia.assinatura;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class PlanoAssinaturaControllerIntegrationTest extends IntegrationTestBase {

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
    void crudCompletoDePlanoDeAssinatura() throws Exception {
        String token = autenticar("admin.plano@teste.com", Perfil.ADMIN, "198.51.103.1");
        UUID servicoUuid = criarServico(token, "Corte Clube", 30, "50.00");

        String resposta = mockMvc.perform(post("/api/planos-assinatura")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Plano Mensal",
                                  "descricao": "1 corte por mes",
                                  "precoMensal": 89.90,
                                  "cortesIncluidosPorCiclo": 1,
                                  "percentualDescontoAdicional": 10,
                                  "servicosInclusosUuids": ["%s"]
                                }
                                """.formatted(servicoUuid)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Plano Mensal"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID planoUuid = UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());

        mockMvc.perform(get("/api/planos-assinatura")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/planos-assinatura/" + planoUuid)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Plano Mensal Atualizado",
                                  "precoMensal": 99.90,
                                  "cortesIncluidosPorCiclo": 2,
                                  "percentualDescontoAdicional": 15,
                                  "servicosInclusosUuids": ["%s"]
                                }
                                """.formatted(servicoUuid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Plano Mensal Atualizado"))
                .andExpect(jsonPath("$.cortesIncluidosPorCiclo").value(2));

        mockMvc.perform(patch("/api/planos-assinatura/" + planoUuid + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void apenasAdminOuGerentePodeCriarPlano() throws Exception {
        String tokenAdmin = autenticar("admin.planoperm@teste.com", Perfil.ADMIN, "198.51.103.2");
        UUID servicoUuid = criarServico(tokenAdmin, "Corte Recepcao", 30, "50.00");
        String tokenRecepcao = autenticar("recepcao.plano@teste.com", Perfil.RECEPCAO, "198.51.103.3");

        mockMvc.perform(post("/api/planos-assinatura")
                        .header("Authorization", "Bearer " + tokenRecepcao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Plano Proibido",
                                  "precoMensal": 50.00,
                                  "cortesIncluidosPorCiclo": 1,
                                  "percentualDescontoAdicional": 0,
                                  "servicosInclusosUuids": ["%s"]
                                }
                                """.formatted(servicoUuid)))
                .andExpect(status().isForbidden());
    }

    private UUID criarServico(String token, String nome, int duracaoMinutos, String preco) throws Exception {
        String corpo = """
                {
                  "nome": "%s",
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
