package com.barbearia.assinatura;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
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

import com.barbearia.assinatura.domain.Assinatura;
import com.barbearia.assinatura.domain.StatusAssinatura;
import com.barbearia.assinatura.repository.AssinaturaRepository;
import com.barbearia.assinatura.service.AssinaturaService;
import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.financeiro.domain.ContaReceber;
import com.barbearia.financeiro.domain.StatusContaReceber;
import com.barbearia.financeiro.repository.ContaReceberRepository;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AssinaturaFluxoIntegrationTest extends IntegrationTestBase {

    private static final String SENHA = "SenhaForte123!";
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AssinaturaRepository assinaturaRepository;
    @Autowired
    private ContaReceberRepository contaReceberRepository;
    @Autowired
    private AssinaturaService assinaturaService;

    @Test
    void assinanteConsomeSaldoENoZeradoOProximoServicoViraAdicional() throws Exception {
        String token = autenticar("admin.assinatura1@teste.com", "198.51.104.1");
        UUID clienteUuid = criarCliente(token, "Cliente Assinante", "(19) 99000-3001");
        UUID servicoUuid = criarServico(token, "Corte Clube 1", 30, "60.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Clube 1");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "20:00");
        UUID planoUuid = criarPlano(token, "Plano Basico", "80.00", 1, List.of(servicoUuid));

        String respostaAssinatura = mockMvc.perform(post("/api/assinaturas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteUuid\": \"" + clienteUuid + "\", \"planoUuid\": \"" + planoUuid + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoCortesAtual").value(1))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andReturn().getResponse().getContentAsString();
        UUID assinaturaUuid = UUID.fromString(objectMapper.readTree(respostaAssinatura).get("uuid").asText());

        // Primeiro atendimento: consome o unico corte do saldo, item nasce coberto (valor zero).
        UUID agendamento1 = criarAgendamento(token, clienteUuid, profissionalUuid, servicoUuid, proximaSegunda());
        confirmar(token, agendamento1);
        UUID comanda1 = abrirComanda(token, agendamento1);
        mockMvc.perform(get("/api/comandas/" + comanda1)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.itens[0].cobertoPorAssinatura").value(true))
                .andExpect(jsonPath("$.itens[0].valorLiquido").value(0))
                .andExpect(jsonPath("$.valorTotal").value(0));

        mockMvc.perform(get("/api/assinaturas/" + assinaturaUuid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.saldoCortesAtual").value(0));

        // Segundo atendimento: saldo zerado, servico vira adicional (cobrado avulso).
        // Mesmo dia da grade sincronizada (segunda), horario diferente do primeiro.
        UUID agendamento2 = criarAgendamento(token, clienteUuid, profissionalUuid, servicoUuid,
                proximaSegunda(), LocalTime.of(10, 0));
        confirmar(token, agendamento2);
        UUID comanda2 = abrirComanda(token, agendamento2);
        mockMvc.perform(get("/api/comandas/" + comanda2)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.itens[0].cobertoPorAssinatura").value(false))
                .andExpect(jsonPath("$.itens[0].valorLiquido").value(60.00))
                .andExpect(jsonPath("$.valorTotal").value(60.00));
    }

    @Test
    void clienteNaoPodeTerDuasAssinaturasEmCurso() throws Exception {
        String token = autenticar("admin.assinatura2@teste.com", "198.51.104.2");
        UUID clienteUuid = criarCliente(token, "Cliente Duplicado", "(19) 99000-3002");
        UUID servicoUuid = criarServico(token, "Corte Clube 2", 30, "60.00");
        UUID planoUuid = criarPlano(token, "Plano Duplicado", "80.00", 1, List.of(servicoUuid));

        mockMvc.perform(post("/api/assinaturas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteUuid\": \"" + clienteUuid + "\", \"planoUuid\": \"" + planoUuid + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/assinaturas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteUuid\": \"" + clienteUuid + "\", \"planoUuid\": \"" + planoUuid + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renovacaoReabastecendoSaldoQuandoMensalidadeAnteriorFoiPaga() throws Exception {
        String token = autenticar("admin.assinatura3@teste.com", "198.51.104.3");
        UUID clienteUuid = criarCliente(token, "Cliente Renovacao Paga", "(19) 99000-3003");
        UUID servicoUuid = criarServico(token, "Corte Clube 3", 30, "60.00");
        UUID planoUuid = criarPlano(token, "Plano Renovacao", "80.00", 2, List.of(servicoUuid));
        UUID assinaturaUuid = assinar(token, clienteUuid, planoUuid);

        Assinatura assinatura = assinaturaRepository.findByUuidPublico(assinaturaUuid).orElseThrow();
        LocalDate hoje = LocalDate.now(FUSO);
        assinatura.setDataProximaRenovacao(hoje);
        assinaturaRepository.save(assinatura);
        ContaReceber contaDoCiclo = contaReceberRepository.findTopByAssinaturaOrderByDataVencimentoDesc(assinatura)
                .orElseThrow();
        contaDoCiclo.setStatus(StatusContaReceber.RECEBIDA);
        contaDoCiclo.setDataRecebimento(hoje);
        contaReceberRepository.save(contaDoCiclo);

        assinaturaService.processarRenovacoes();

        Assinatura renovada = assinaturaRepository.findByUuidPublico(assinaturaUuid).orElseThrow();
        assertThat(renovada.getStatus()).isEqualTo(StatusAssinatura.ATIVA);
        assertThat(renovada.getSaldoCortesAtual()).isEqualTo(2);
        assertThat(renovada.getDataProximaRenovacao()).isEqualTo(hoje.plusMonths(1));

        List<ContaReceber> cobrancas = contaReceberRepository.findByCliente_UuidPublicoOrderByDataVencimento(
                clienteUuid);
        assertThat(cobrancas).hasSize(2);
        assertThat(cobrancas.get(1).getStatus()).isEqualTo(StatusContaReceber.PENDENTE);
    }

    @Test
    void falhaDeCobrancaMarcaAssinaturaComoInadimplenteENaoRenova() throws Exception {
        String token = autenticar("admin.assinatura4@teste.com", "198.51.104.4");
        UUID clienteUuid = criarCliente(token, "Cliente Inadimplente", "(19) 99000-3004");
        UUID servicoUuid = criarServico(token, "Corte Clube 4", 30, "60.00");
        UUID planoUuid = criarPlano(token, "Plano Inadimplencia", "80.00", 2, List.of(servicoUuid));
        UUID assinaturaUuid = assinar(token, clienteUuid, planoUuid);

        Assinatura assinatura = assinaturaRepository.findByUuidPublico(assinaturaUuid).orElseThrow();
        LocalDate hoje = LocalDate.now(FUSO);
        assinatura.setDataProximaRenovacao(hoje);
        assinaturaRepository.save(assinatura);
        // Mensalidade do ciclo anterior segue PENDENTE (nao paga) ate a data de renovacao.

        assinaturaService.processarRenovacoes();

        Assinatura resultado = assinaturaRepository.findByUuidPublico(assinaturaUuid).orElseThrow();
        assertThat(resultado.getStatus()).isEqualTo(StatusAssinatura.INADIMPLENTE);
        assertThat(resultado.getSaldoCortesAtual()).isEqualTo(2);
        assertThat(resultado.getDataProximaRenovacao()).isEqualTo(hoje);

        List<ContaReceber> cobrancas = contaReceberRepository.findByCliente_UuidPublicoOrderByDataVencimento(
                clienteUuid);
        assertThat(cobrancas).hasSize(1);

        // "Retry automatico": rodar de novo sem alterar nada mantem INADIMPLENTE, sem duplicar cobranca.
        assinaturaService.processarRenovacoes();
        assertThat(contaReceberRepository.findByCliente_UuidPublicoOrderByDataVencimento(clienteUuid)).hasSize(1);
    }

    @Test
    void relatorioDiferenciaReceitaDeAssinaturaEDeAvulso() throws Exception {
        String token = autenticar("admin.assinatura5@teste.com", "198.51.104.5");
        UUID clienteAssinanteUuid = criarCliente(token, "Cliente Relatorio Assinante", "(19) 99000-3005");
        UUID clienteAvulsoUuid = criarCliente(token, "Cliente Relatorio Avulso", "(19) 99000-3006");
        UUID servicoUuid = criarServico(token, "Corte Relatorio", 30, "70.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Relatorio");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGrade(token, profissionalUuid, 1, "09:00", "20:00");

        // Receita avulsa: comanda fechada de um cliente sem assinatura.
        UUID agendamentoAvulso = criarAgendamento(token, clienteAvulsoUuid, profissionalUuid, servicoUuid,
                proximaSegunda());
        confirmar(token, agendamentoAvulso);
        UUID comandaAvulsa = abrirComanda(token, agendamentoAvulso);
        definirFormaPagamento(token, comandaAvulsa, "DINHEIRO");
        mockMvc.perform(post("/api/comandas/" + comandaAvulsa + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Receita de assinatura: mensalidade recebida no mes corrente.
        UUID planoUuid = criarPlano(token, "Plano Relatorio", "90.00", 1, List.of(servicoUuid));
        UUID assinaturaUuid = assinar(token, clienteAssinanteUuid, planoUuid);
        Assinatura assinatura = assinaturaRepository.findByUuidPublico(assinaturaUuid).orElseThrow();
        ContaReceber mensalidade = contaReceberRepository.findTopByAssinaturaOrderByDataVencimentoDesc(assinatura)
                .orElseThrow();
        mensalidade.setStatus(StatusContaReceber.RECEBIDA);
        mensalidade.setDataRecebimento(LocalDate.now(FUSO));
        contaReceberRepository.save(mensalidade);

        YearMonth mesCorrente = YearMonth.now(FUSO);
        mockMvc.perform(get("/api/assinaturas/relatorio-receita")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", mesCorrente.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receitaAssinaturas").value(90.00))
                .andExpect(jsonPath("$.receitaAvulsa").value(70.00));
    }

    private UUID criarPlano(String token, String nome, String precoMensal, int cortesIncluidos,
            List<UUID> servicosUuids) throws Exception {
        String servicosJson = servicosUuids.stream().map(uuid -> "\"" + uuid + "\"")
                .reduce((a, b) -> a + "," + b).orElse("");
        String corpo = """
                {
                  "nome": "%s",
                  "precoMensal": %s,
                  "cortesIncluidosPorCiclo": %d,
                  "percentualDescontoAdicional": 0,
                  "servicosInclusosUuids": [%s]
                }
                """.formatted(nome, precoMensal, cortesIncluidos, servicosJson);

        String resposta = mockMvc.perform(post("/api/planos-assinatura")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
    }

    private UUID assinar(String token, UUID clienteUuid, UUID planoUuid) throws Exception {
        String resposta = mockMvc.perform(post("/api/assinaturas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteUuid\": \"" + clienteUuid + "\", \"planoUuid\": \"" + planoUuid + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
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

    private void confirmar(String token, UUID agendamentoUuid) throws Exception {
        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/confirmar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private LocalDate proximaSegunda() {
        return ZonedDateTime.now(FUSO).toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private UUID criarAgendamento(String token, UUID clienteUuid, UUID profissionalUuid, UUID servicoUuid,
            LocalDate data) throws Exception {
        return criarAgendamento(token, clienteUuid, profissionalUuid, servicoUuid, data, LocalTime.of(9, 0));
    }

    private UUID criarAgendamento(String token, UUID clienteUuid, UUID profissionalUuid, UUID servicoUuid,
            LocalDate data, LocalTime hora) throws Exception {
        Instant inicio = ZonedDateTime.of(data, hora, FUSO).toInstant();
        String corpo = """
                {
                  "clienteUuid": "%s",
                  "profissionalUuid": "%s",
                  "servicoUuids": ["%s"],
                  "inicio": "%s",
                  "observacao": null
                }
                """.formatted(clienteUuid, profissionalUuid, servicoUuid, inicio);

        String resposta = mockMvc.perform(post("/api/agendamentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
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
                  "email": "profissional.assinatura@teste.com",
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

    private String autenticar(String email, String ipSimulado) throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuario de teste");
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPerfil(Perfil.ADMIN);
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
