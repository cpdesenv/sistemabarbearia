package com.barbearia.cliente;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
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
    void deveRetornarFichaComHistoricoDeAgendamentoComandaENotaFiscal() throws Exception {
        String token = autenticar("admin.fichacomhistorico@teste.com", Perfil.ADMIN, "192.0.2.33");
        UUID clienteUuid = criarCliente(token, "Cliente Com Historico", "(19) 90000-1111");
        UUID servicoUuid = criarServico(token, "Corte Historico", 45, "50.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Historico");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "18:00");

        ZoneId fuso = ZoneId.of("America/Sao_Paulo");
        LocalDate proximaSegunda = ZonedDateTime.now(fuso).toLocalDate()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Instant inicio = ZonedDateTime.of(proximaSegunda, LocalTime.of(9, 0), fuso).toInstant();

        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, servicoUuid, inicio);
        confirmar(token, agendamentoUuid);
        UUID comandaUuid = abrirComanda(token, agendamentoUuid);
        definirFormaPagamento(token, comandaUuid, "DINHEIRO");
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/clientes/" + clienteUuid + "/ficha")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agendamentos.length()").value(1))
                .andExpect(jsonPath("$.agendamentos[0].uuid").value(agendamentoUuid.toString()))
                .andExpect(jsonPath("$.atendimentos.length()").value(1))
                .andExpect(jsonPath("$.atendimentos[0].uuid").value(comandaUuid.toString()))
                .andExpect(jsonPath("$.atendimentos[0].status").value("FECHADA"))
                .andExpect(jsonPath("$.notasFiscais.length()").value(1))
                .andExpect(jsonPath("$.notasFiscais[0].numero").exists());
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
                  "email": "profissional.ficha@teste.com",
                  "telefone": "11900000000",
                  "corAgenda": "#3F51B5",
                  "comissaoPercentualPadrao": 30.00
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

    private void sincronizarGrade(String token, UUID profissionalUuid, int diaSemana, String horaInicio,
            String horaFim) throws Exception {
        String corpo = "[{\"diaSemana\": " + diaSemana + ", \"horaInicio\": \"" + horaInicio + "\", \"horaFim\": \""
                + horaFim + "\"}]";
        mockMvc.perform(put("/api/profissionais/" + profissionalUuid + "/grade-horaria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());
    }

    private UUID criarAgendamento(String token, UUID clienteUuid, UUID profissionalUuid, UUID servicoUuid,
            Instant inicio) throws Exception {
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
