package com.barbearia.barbearia;

import java.util.List;

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
class BarbeariaControllerIntegrationTest extends IntegrationTestBase {

    private static final String SENHA = "SenhaForte123!";
    private static final String CORPO_VALIDO = """
            {
              "nome": "Cortes Cavalinho",
              "cnpj": "12.345.678/0001-90",
              "telefone": "11999998888",
              "email": "contato@cortescavalinho.com.br",
              "logradouro": "Rua das Tesouras",
              "numero": "100",
              "complemento": null,
              "bairro": "Centro",
              "cidade": "Campinas",
              "uf": "SP",
              "cep": "13000-000",
              "fusoHorario": "America/Sao_Paulo",
              "antecedenciaMinimaAgendamentoMinutos": 30,
              "antecedenciaMaximaAgendamentoDias": 45,
              "antecedenciaMinimaCancelamentoMinutos": 120
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
    void deveExigirAutenticacaoParaConsultar() throws Exception {
        mockMvc.perform(get("/api/barbearia"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornarConfiguracaoSemeadaPelaMigration() throws Exception {
        String token = autenticar("barbeiro.barbearia@teste.com", Perfil.BARBEIRO, "192.0.2.1");

        mockMvc.perform(get("/api/barbearia").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Minha Barbearia"))
                .andExpect(jsonPath("$.fusoHorario").value("America/Sao_Paulo"));
    }

    @Test
    void deveAtualizarComoAdminERegistrarAuditoria() throws Exception {
        String token = autenticar("admin.barbearia@teste.com", Perfil.ADMIN, "192.0.2.2");

        mockMvc.perform(put("/api/barbearia")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Cortes Cavalinho"))
                .andExpect(jsonPath("$.cidade").value("Campinas"))
                .andExpect(jsonPath("$.antecedenciaMaximaAgendamentoDias").value(45));

        mockMvc.perform(get("/api/barbearia").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Cortes Cavalinho"));

        List<?> registros = auditoriaRepository.findAll().stream()
                .filter(a -> "BARBEARIA_ATUALIZADA".equals(a.getOperacao()))
                .toList();
        assertThat(registros).isNotEmpty();
    }

    @Test
    void deveRecusarAtualizacaoComPerfilInsuficiente() throws Exception {
        String token = autenticar("barbeiro.semacesso@teste.com", Perfil.BARBEIRO, "192.0.2.3");

        mockMvc.perform(put("/api/barbearia")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRecusarAtualizacaoComNomeEmBranco() throws Exception {
        String token = autenticar("admin.validacao@teste.com", Perfil.ADMIN, "192.0.2.4");
        String corpoInvalido = CORPO_VALIDO.replace("\"Cortes Cavalinho\"", "\"\"");

        mockMvc.perform(put("/api/barbearia")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("nome"));
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
