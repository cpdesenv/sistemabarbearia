package com.barbearia.ia;

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
import com.barbearia.cliente.domain.Cliente;
import com.barbearia.cliente.domain.OrigemCadastro;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.ia.domain.ConfiguracaoIa;
import com.barbearia.ia.repository.ConfiguracaoIaRepository;
import com.barbearia.mensageria.domain.Conversa;
import com.barbearia.mensageria.domain.ModoAtendimento;
import com.barbearia.mensageria.repository.ConversaRepository;
import com.barbearia.shared.IntegrationTestBase;
import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

/**
 * Cobre o endpoint administrativo de configuracao do agente de IA (PRD, Fase
 * 10): kill switch, limite de turnos e teto de custo mensal. A restauracao do
 * estado padrao (ativo=true) no fim de cada teste evita que uma classe
 * seguinte da suite rode com a IA desligada.
 */
@Transactional
class ConfiguracaoIaControllerIntegrationTest extends IntegrationTestBase {

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
    private ConfiguracaoIaRepository configuracaoIaRepository;
    @Autowired
    private ConversaRepository conversaRepository;
    @Autowired
    private ClienteRepository clienteRepository;

    @Test
    void deveExigirAutenticacaoParaObter() throws Exception {
        mockMvc.perform(get("/api/configuracoes/ia")).andExpect(status().isUnauthorized());
    }

    @Test
    void obterDeveMostrarValoresPadrao() throws Exception {
        String token = autenticar("admin.ia.obter@teste.com", Perfil.ADMIN, "203.0.113.60");

        mockMvc.perform(get("/api/configuracoes/ia").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.limiteTurnos").value(25));
    }

    @Test
    void apenasAdminPodeAtualizar() throws Exception {
        String tokenRecepcao = autenticar("recepcao.ia@teste.com", Perfil.RECEPCAO, "203.0.113.61");

        mockMvc.perform(put("/api/configuracoes/ia")
                        .header("Authorization", "Bearer " + tokenRecepcao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\": false, \"limiteTurnos\": 25, \"tetoCustoMensalCentavos\": 10000}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void desligarOKillSwitchEscalaTodasAsConversasEmModoIaImediatamente() throws Exception {
        String token = autenticar("admin.ia.killswitch@teste.com", Perfil.ADMIN, "203.0.113.62");
        Conversa conversaEmIa = criarConversaEmModo(ModoAtendimento.IA);
        Conversa conversaJaHumana = criarConversaEmModo(ModoAtendimento.HUMANO);

        try {
            mockMvc.perform(put("/api/configuracoes/ia")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ativo\": false, \"limiteTurnos\": 25, \"tetoCustoMensalCentavos\": 10000}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ativo").value(false));

            assertThat(conversaRepository.findByUuidPublico(conversaEmIa.getUuidPublico()).orElseThrow()
                    .getModoAtendimento()).isEqualTo(ModoAtendimento.HUMANO);
            assertThat(conversaRepository.findByUuidPublico(conversaJaHumana.getUuidPublico()).orElseThrow()
                    .getModoAtendimento()).isEqualTo(ModoAtendimento.HUMANO);
        } finally {
            ConfiguracaoIa configuracao = configuracaoIaRepository.findById(ConfiguracaoIa.ID_SINGLETON).orElseThrow();
            configuracao.setAtivo(true);
            configuracaoIaRepository.save(configuracao);
        }
    }

    private Conversa criarConversaEmModo(ModoAtendimento modo) {
        Cliente cliente = new Cliente();
        cliente.setNome("Cliente Teste Config IA " + System.nanoTime());
        cliente.setTelefone("+5519" + (900000000L + (System.nanoTime() % 99999999L)));
        cliente.setOptInWhatsapp(true);
        cliente.setOrigemCadastro(OrigemCadastro.WHATSAPP);
        cliente = clienteRepository.save(cliente);

        Conversa conversa = new Conversa();
        conversa.setCliente(cliente);
        conversa.setTelefoneE164(cliente.getTelefone());
        conversa.setModoAtendimento(modo);
        return conversaRepository.save(conversa);
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
