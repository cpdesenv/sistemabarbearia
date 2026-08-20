package com.barbearia.cliente;

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
class ClienteControllerIntegrationTest extends IntegrationTestBase {

    private static final String SENHA = "SenhaForte123!";
    private static final String CORPO_VALIDO = """
            {
              "nome": "Joao da Silva",
              "telefone": "(19) 99999-8888",
              "email": "joao@teste.com",
              "cpf": "111.444.777-35",
              "optInWhatsapp": true,
              "consentimentoLgpd": true
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
        mockMvc.perform(get("/api/clientes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveCriarClienteComoRecepcaoENormalizarTelefone() throws Exception {
        String token = autenticar("recepcao.criar@teste.com", Perfil.RECEPCAO, "192.0.2.20");

        mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Joao da Silva"))
                .andExpect(jsonPath("$.telefone").value("+5519999998888"))
                .andExpect(jsonPath("$.cpf").value("11144477735"))
                .andExpect(jsonPath("$.origemCadastro").value("PAINEL"))
                .andExpect(jsonPath("$.consentimentoLgpd").value(true))
                .andExpect(jsonPath("$.consentimentoLgpdEm").isNotEmpty());

        List<?> registros = auditoriaRepository.findAll().stream()
                .filter(a -> "CLIENTE_CADASTRADO".equals(a.getOperacao()))
                .toList();
        assertThat(registros).isNotEmpty();
    }

    @Test
    void deveRecusarCriacaoComPerfilInsuficiente() throws Exception {
        String token = autenticar("barbeiro.criar@teste.com", Perfil.BARBEIRO, "192.0.2.21");

        mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRecusarCpfInvalido() throws Exception {
        String token = autenticar("admin.cpf@teste.com", Perfil.ADMIN, "192.0.2.22");
        String corpo = CORPO_VALIDO.replace("111.444.777-35", "111.111.111-11");

        mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("CPF invalido."));
    }

    @Test
    void deveRecusarTelefoneInvalido() throws Exception {
        String token = autenticar("admin.telefone@teste.com", Perfil.ADMIN, "192.0.2.23");
        String corpo = CORPO_VALIDO.replace("(19) 99999-8888", "123");

        mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Telefone invalido."));
    }

    @Test
    void deveAvisarSobreTelefoneDuplicadoEOferecerClienteExistente() throws Exception {
        String token = autenticar("admin.duplicado@teste.com", Perfil.ADMIN, "192.0.2.24");
        criarCliente(token, "Joao da Silva", "(19) 98888-7777");

        String corpo = CORPO_VALIDO
                .replace("Joao da Silva", "Joao Silva Junior")
                .replace("(19) 99999-8888", "19988887777")
                .replace("111.444.777-35", "");

        mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("CLIENTE_DUPLICADO"))
                .andExpect(jsonPath("$.clienteExistente.nome").value("Joao da Silva"))
                .andExpect(jsonPath("$.clienteExistente.telefone").value("+5519988887777"));
    }

    @Test
    void deveListarComBuscaPorNomeOuTelefone() throws Exception {
        String token = autenticar("admin.listar@teste.com", Perfil.ADMIN, "192.0.2.25");
        criarCliente(token, "Maria Souza", "(19) 97777-1111");
        criarCliente(token, "Pedro Alves", "(19) 96666-2222");

        mockMvc.perform(get("/api/clientes")
                        .header("Authorization", "Bearer " + token)
                        .param("busca", "maria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Maria Souza"));
    }

    @Test
    void deveAtualizarCliente() throws Exception {
        String token = autenticar("admin.atualizar@teste.com", Perfil.ADMIN, "192.0.2.26");
        UUID uuid = criarCliente(token, "Cliente Original", "(19) 95555-3333");

        String corpoAtualizado = """
                {
                  "nome": "Cliente Renomeado",
                  "telefone": "(19) 95555-3333",
                  "email": "novo@teste.com",
                  "optInWhatsapp": false,
                  "consentimentoLgpd": true
                }
                """;

        mockMvc.perform(put("/api/clientes/" + uuid)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAtualizado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Cliente Renomeado"))
                .andExpect(jsonPath("$.optInWhatsapp").value(false));
    }

    @Test
    void deveRetornarFichaComHistoricoVazio() throws Exception {
        String token = autenticar("admin.ficha@teste.com", Perfil.ADMIN, "192.0.2.27");
        UUID uuid = criarCliente(token, "Cliente Ficha", "(19) 94444-5555");

        mockMvc.perform(get("/api/clientes/" + uuid + "/ficha")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.nome").value("Cliente Ficha"))
                .andExpect(jsonPath("$.agendamentos.length()").value(0))
                .andExpect(jsonPath("$.atendimentos.length()").value(0))
                .andExpect(jsonPath("$.notasFiscais.length()").value(0));
    }

    @Test
    void deveExportarDadosDoClienteComoAdmin() throws Exception {
        String token = autenticar("admin.exportar@teste.com", Perfil.ADMIN, "192.0.2.28");
        UUID uuid = criarCliente(token, "Cliente Exportado", "(19) 93333-6666");

        mockMvc.perform(get("/api/clientes/" + uuid + "/exportar-dados")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.nome").value("Cliente Exportado"))
                .andExpect(jsonPath("$.exportadoEm").isNotEmpty());
    }

    @Test
    void deveRecusarExportacaoParaRecepcao() throws Exception {
        String tokenAdmin = autenticar("admin.exportar2@teste.com", Perfil.ADMIN, "192.0.2.29");
        UUID uuid = criarCliente(tokenAdmin, "Cliente Restrito", "(19) 92222-7777");

        String tokenRecepcao = autenticar("recepcao.exportar@teste.com", Perfil.RECEPCAO, "192.0.2.30");

        mockMvc.perform(get("/api/clientes/" + uuid + "/exportar-dados")
                        .header("Authorization", "Bearer " + tokenRecepcao))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveAnonimizarCliente() throws Exception {
        String token = autenticar("admin.anonimizar@teste.com", Perfil.ADMIN, "192.0.2.31");
        UUID uuid = criarCliente(token, "Cliente Para Anonimizar", "(19) 91111-8888");

        mockMvc.perform(post("/api/clientes/" + uuid + "/anonimizar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Solicitacao do titular\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Cliente anonimizado"))
                .andExpect(jsonPath("$.telefone").doesNotExist());

        mockMvc.perform(get("/api/clientes/" + uuid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Cliente anonimizado"))
                .andExpect(jsonPath("$.anonimizado").value(true));

        List<String> operacoes = auditoriaRepository.findAll().stream()
                .map(a -> a.getOperacao())
                .toList();
        assertThat(operacoes).contains("CLIENTE_ANONIMIZADO");
    }

    @Test
    void deveRetornar404ParaUuidInexistente() throws Exception {
        String token = autenticar("admin.notfound@teste.com", Perfil.ADMIN, "192.0.2.32");

        mockMvc.perform(get("/api/clientes/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
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
