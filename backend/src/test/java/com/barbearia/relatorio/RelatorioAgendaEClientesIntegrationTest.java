package com.barbearia.relatorio;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.financeiro.domain.Comanda;
import com.barbearia.financeiro.repository.ComandaRepository;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

@Transactional
class RelatorioAgendaEClientesIntegrationTest extends IntegrationTestBase {

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
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private ComandaRepository comandaRepository;

    @Test
    void relatoriosDeAgendaEClientesDevemRefletirFinalizadosCanceladosFaltasENovosVsRecorrentes() throws Exception {
        String token = autenticar("admin.relatorioag@teste.com", "198.51.107.1");

        UUID servicoUuid = criarServico(token, "Corte Agenda", 30, "80.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Agenda");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGradeDiaInteiroHoje(token, profissionalUuid);

        UUID clienteA = criarCliente(token, "Cliente A Retorno", "(19) 99000-9001");
        UUID clienteB = criarCliente(token, "Cliente B Retorno", "(19) 99000-9002");

        LocalDate diaAntigo = LocalDate.now(FUSO).minusDays(10);
        LocalDate diaTeste = LocalDate.now(FUSO).minusDays(5);

        // Dia antigo: cliente B tem seu primeiro atendimento (estabelece historico).
        UUID comandaAntigaUuid = agendarConfirmarEFechar(token, clienteB, profissionalUuid, servicoUuid, 0);
        backdatarAgendamentoEComanda(comandaAntigaUuid, diaAntigo);

        // Dia de teste: cliente A (novo) e cliente B (recorrente) sao atendidos.
        UUID comandaNovoUuid = agendarConfirmarEFechar(token, clienteA, profissionalUuid, servicoUuid, 1);
        backdatarAgendamentoEComanda(comandaNovoUuid, diaTeste);

        UUID comandaRecorrenteUuid = agendarConfirmarEFechar(token, clienteB, profissionalUuid, servicoUuid, 2);
        backdatarAgendamentoEComanda(comandaRecorrenteUuid, diaTeste);

        // Dia de teste: um cancelamento e uma falta do mesmo profissional.
        UUID agendamentoCanceladoUuid = criarAgendamento(token, clienteA, profissionalUuid, servicoUuid, 3);
        backdatarAgendamento(agendamentoCanceladoUuid, diaTeste);
        cancelar(token, agendamentoCanceladoUuid);

        UUID agendamentoFaltaUuid = criarAgendamento(token, clienteB, profissionalUuid, servicoUuid, 4);
        backdatarAgendamento(agendamentoFaltaUuid, diaTeste);
        marcarNaoComparecimento(token, agendamentoFaltaUuid);

        mockMvc.perform(post("/api/relatorios/reprocessar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataInicial\": \"" + diaTeste + "\", \"dataFinal\": \"" + diaTeste + "\"}"))
                .andExpect(status().isNoContent());

        String respostaClientes = mockMvc.perform(get("/api/relatorios/clientes")
                        .header("Authorization", "Bearer " + token)
                        .param("dataInicial", diaTeste.toString())
                        .param("dataFinal", diaTeste.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode clientes = objectMapper.readTree(respostaClientes);
        assertThat(clientes.get("clientesNovos").asInt()).isEqualTo(1);
        assertThat(clientes.get("clientesRecorrentes").asInt()).isEqualTo(1);
        assertThat(clientes.get("atendimentosTotais").asInt()).isEqualTo(2);
        assertThat(clientes.get("taxaDeRetorno").asDouble()).isEqualTo(50.00);

        String respostaAgenda = mockMvc.perform(get("/api/relatorios/agenda")
                        .header("Authorization", "Bearer " + token)
                        .param("dataInicial", diaTeste.toString())
                        .param("dataFinal", diaTeste.toString())
                        .param("profissionalUuid", profissionalUuid.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode agenda = objectMapper.readTree(respostaAgenda);
        assertThat(agenda.get("quantidadeFinalizados").asInt()).isEqualTo(2);
        assertThat(agenda.get("quantidadeCancelados").asInt()).isEqualTo(1);
        assertThat(agenda.get("quantidadeNaoCompareceu").asInt()).isEqualTo(1);
        assertThat(agenda.get("taxaOcupacao").asDouble()).isGreaterThan(0).isLessThan(100);
        assertThat(agenda.get("porProfissional")).anySatisfy(item ->
                assertThat(item.get("profissionalNome").asText()).isEqualTo("Prof Agenda"));
    }

    private UUID agendarConfirmarEFechar(String token, UUID clienteUuid, UUID profissionalUuid, UUID servicoUuid,
            int indiceSlot) throws Exception {
        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, servicoUuid, indiceSlot);
        confirmar(token, agendamentoUuid);
        UUID comandaUuid = abrirComanda(token, agendamentoUuid);
        definirFormaPagamento(token, comandaUuid, "PIX");
        fecharComanda(token, comandaUuid);
        return comandaUuid;
    }

    /** Move o agendamento/comanda recem-criados para uma data no passado, preservando o horario do dia. */
    private void backdatarAgendamentoEComanda(UUID comandaUuid, LocalDate novaData) {
        Comanda comanda = comandaRepository.findByUuidPublico(comandaUuid).orElseThrow();
        backdatarEntidadeAgendamento(comanda.getAgendamento(), novaData);
        comanda.setFechadaEm(comanda.getAgendamento().getInicio());
        comandaRepository.save(comanda);
    }

    private void backdatarAgendamento(UUID agendamentoUuid, LocalDate novaData) {
        Agendamento agendamento = agendamentoRepository.findByUuidPublico(agendamentoUuid).orElseThrow();
        backdatarEntidadeAgendamento(agendamento, novaData);
    }

    private void backdatarEntidadeAgendamento(Agendamento agendamento, LocalDate novaData) {
        ZonedDateTime novoInicioZoned = ZonedDateTime.of(novaData, agendamento.getInicio().atZone(FUSO).toLocalTime(),
                FUSO);
        long duracaoMinutos = Duration.between(agendamento.getInicio(), agendamento.getFim()).toMinutes();
        agendamento.setInicio(novoInicioZoned.toInstant());
        agendamento.setFim(novoInicioZoned.plusMinutes(duracaoMinutos).toInstant());
        agendamentoRepository.save(agendamento);
    }

    private void cancelar(String token, UUID agendamentoUuid) throws Exception {
        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/cancelar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Teste relatorio\"}"))
                .andExpect(status().isOk());
    }

    private void marcarNaoComparecimento(String token, UUID agendamentoUuid) throws Exception {
        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/nao-compareceu")
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

    private void fecharComanda(String token, UUID comandaUuid) throws Exception {
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void confirmar(String token, UUID agendamentoUuid) throws Exception {
        mockMvc.perform(post("/api/agendamentos/" + agendamentoUuid + "/confirmar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * Slot fixo em "amanha" as 09:00 + indiceSlot*40min (nunca "agora + N min"):
     * o teste retroage esses agendamentos para o dia de teste logo em seguida,
     * entao o horario real de criacao e' irrelevante — so precisa ser um
     * horario futuro valido que nunca atravesse a virada do dia, o que "agora
     * + N min" nao garante se o teste rodar perto da meia-noite local.
     */
    private UUID criarAgendamento(String token, UUID clienteUuid, UUID profissionalUuid, UUID servicoUuid,
            int indiceSlot) throws Exception {
        LocalDate amanha = LocalDate.now(FUSO).plusDays(1);
        Instant inicio = ZonedDateTime.of(amanha, LocalTime.of(9, 0), FUSO)
                .plusMinutes(indiceSlot * 40L)
                .toInstant();
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
                  "email": "profissional.relatorioag@teste.com",
                  "telefone": "11900000000",
                  "corAgenda": "#3F51B5",
                  "comissaoPercentualPadrao": 20.00
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

    /**
     * Sincroniza os 7 dias da semana (nao so' hoje): os agendamentos deste teste
     * sao criados com deslocamentos de ate 165 minutos a partir de "agora" (antes
     * de serem retroagidos para o dia de teste) e podem cair no dia seguinte se o
     * teste rodar tarde da noite — sincronizar so' hoje falharia por falta de
     * janela nesse caso.
     */
    private void sincronizarGradeDiaInteiroHoje(String token, UUID profissionalUuid) throws Exception {
        StringBuilder corpo = new StringBuilder("[");
        for (int diaSemana = 1; diaSemana <= 7; diaSemana++) {
            if (diaSemana > 1) {
                corpo.append(",");
            }
            corpo.append("{\"diaSemana\": ").append(diaSemana).append(", \"horaInicio\": \"00:00\", \"horaFim\": \"23:59\"}");
        }
        corpo.append("]");

        mockMvc.perform(put("/api/profissionais/" + profissionalUuid + "/grade-horaria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo.toString()))
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
