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
class RelatorioProdutoEHeatmapIntegrationTest extends IntegrationTestBase {

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
    void relatorioDeProdutosDeveRefletirQuantidadeReceitaEMargem() throws Exception {
        String token = autenticar("admin.relatorioprod@teste.com", "198.51.107.2");

        UUID servicoUuid = criarServico(token, "Corte Produto", 30, "80.00");
        UUID profissionalUuid = criarProfissional(token, "Prof Produto");
        vincularServico(token, profissionalUuid, servicoUuid);
        sincronizarGradeDiaInteiroHoje(token, profissionalUuid);
        UUID clienteUuid = criarCliente(token, "Cliente Produto", "(19) 99000-9101");
        UUID produtoUuid = criarProduto(token, "Pomada Modeladora", "50.00", "20.00");
        adicionarEstoque(token, produtoUuid, 10);

        LocalDate diaTeste = LocalDate.now(FUSO).minusDays(6);

        UUID agendamentoUuid = criarAgendamento(token, clienteUuid, profissionalUuid, servicoUuid, 0);
        confirmar(token, agendamentoUuid);
        UUID comandaUuid = abrirComanda(token, agendamentoUuid);
        adicionarItemProduto(token, comandaUuid, produtoUuid, 3);
        definirFormaPagamento(token, comandaUuid, "PIX");
        fecharComanda(token, comandaUuid);
        backdatarAgendamentoEComanda(comandaUuid, diaTeste);

        mockMvc.perform(post("/api/relatorios/reprocessar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataInicial\": \"" + diaTeste + "\", \"dataFinal\": \"" + diaTeste + "\"}"))
                .andExpect(status().isNoContent());

        String resposta = mockMvc.perform(get("/api/relatorios/produtos")
                        .header("Authorization", "Bearer " + token)
                        .param("dataInicial", diaTeste.toString())
                        .param("dataFinal", diaTeste.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode relatorio = objectMapper.readTree(resposta);

        assertThat(relatorio.get("valorTotal").asDouble()).isEqualTo(150.00);
        assertThat(relatorio.get("custoTotal").asDouble()).isEqualTo(60.00);
        assertThat(relatorio.get("margemTotal").asDouble()).isEqualTo(90.00);
        assertThat(relatorio.get("margemPercentual").asDouble()).isEqualTo(60.00);
        assertThat(relatorio.get("porProduto")).anySatisfy(item -> {
            assertThat(item.get("nome").asText()).isEqualTo("Pomada Modeladora");
            assertThat(item.get("quantidadeVendida").asInt()).isEqualTo(3);
        });

        String respostaHeatmap = mockMvc.perform(get("/api/relatorios/heatmap-horarios")
                        .header("Authorization", "Bearer " + token)
                        .param("dataInicial", diaTeste.toString())
                        .param("dataFinal", diaTeste.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode heatmap = objectMapper.readTree(respostaHeatmap);

        int diaSemanaEsperado = diaTeste.getDayOfWeek().getValue();
        assertThat(heatmap.get("celulas")).anySatisfy(celula -> {
            assertThat(celula.get("diaSemana").asInt()).isEqualTo(diaSemanaEsperado);
            assertThat(celula.get("hora").asInt()).isEqualTo(9);
            assertThat(celula.get("quantidadeFinalizados").asInt()).isEqualTo(1);
        });
    }

    private UUID criarProduto(String token, String nome, String precoVenda, String precoCusto) throws Exception {
        String corpo = """
                {
                  "nome": "%s",
                  "precoVenda": %s,
                  "precoCusto": %s,
                  "estoqueMinimo": 1
                }
                """.formatted(nome, precoVenda, precoCusto);

        String resposta = mockMvc.perform(post("/api/produtos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("uuid").asText());
    }

    private void adicionarEstoque(String token, UUID produtoUuid, int quantidade) throws Exception {
        mockMvc.perform(post("/api/produtos/" + produtoUuid + "/entrada-estoque")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\": " + quantidade + "}"))
                .andExpect(status().isOk());
    }

    private void adicionarItemProduto(String token, UUID comandaUuid, UUID produtoUuid, int quantidade)
            throws Exception {
        String corpo = "{\"produtoUuid\": \"" + produtoUuid + "\", \"quantidade\": " + quantidade + "}";
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/itens/produto")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());
    }

    /** Move o agendamento/comanda recem-criados para uma data no passado, preservando o horario do dia. */
    private void backdatarAgendamentoEComanda(UUID comandaUuid, LocalDate novaData) {
        Comanda comanda = comandaRepository.findByUuidPublico(comandaUuid).orElseThrow();
        Agendamento agendamento = comanda.getAgendamento();
        ZonedDateTime novoInicioZoned = ZonedDateTime.of(novaData, agendamento.getInicio().atZone(FUSO).toLocalTime(),
                FUSO);
        long duracaoMinutos = Duration.between(agendamento.getInicio(), agendamento.getFim()).toMinutes();
        agendamento.setInicio(novoInicioZoned.toInstant());
        agendamento.setFim(novoInicioZoned.plusMinutes(duracaoMinutos).toInstant());
        agendamentoRepository.save(agendamento);
        comanda.setFechadaEm(agendamento.getInicio());
        comandaRepository.save(comanda);
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

    private void fecharComanda(String token, UUID comandaUuid) throws Exception {
        mockMvc.perform(post("/api/comandas/" + comandaUuid + "/fechar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * Slot fixo em "amanha" as 09:00 + indiceSlot*40min (nunca "agora + N min"):
     * o teste retroage esse agendamento para o dia de teste logo em seguida,
     * entao o horario real de criacao e' irrelevante — so precisa ser um
     * horario futuro valido que nunca atravesse a virada do dia.
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
                  "email": "profissional.relatorioprod@teste.com",
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
     * Sincroniza os 7 dias da semana (nao so' hoje): o agendamento deste teste
     * e' criado com deslocamento a partir de "amanha" (antes de ser retroagido
     * para o dia de teste) e pode cair um dia depois se o teste rodar tarde da
     * noite — sincronizar so' hoje falharia por falta de janela nesse caso.
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
