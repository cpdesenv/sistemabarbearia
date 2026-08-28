package com.barbearia.ia.tools;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import com.barbearia.agenda.domain.OrigemAgendamento;
import com.barbearia.agenda.dto.AgendamentoDto;
import com.barbearia.agenda.dto.AgendamentoServicoDto;
import com.barbearia.agenda.dto.CancelarAgendamentoRequest;
import com.barbearia.agenda.dto.SalvarAgendamentoRequest;
import com.barbearia.agenda.dto.SlotDisponivelDto;
import com.barbearia.agenda.service.AgendamentoService;
import com.barbearia.agenda.service.AvailabilityService;
import com.barbearia.assinatura.domain.Assinatura;
import com.barbearia.assinatura.domain.StatusAssinatura;
import com.barbearia.assinatura.repository.AssinaturaRepository;
import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.cliente.domain.Cliente;
import com.barbearia.cliente.domain.OrigemCadastro;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.ia.gateway.ChamadaFerramenta;
import com.barbearia.ia.gateway.DefinicaoFerramentaIa;
import com.barbearia.ia.gateway.ResultadoFerramenta;
import com.barbearia.mensageria.domain.Conversa;
import com.barbearia.mensageria.domain.ModoAtendimento;
import com.barbearia.mensageria.repository.ConversaRepository;
import com.barbearia.profissional.dto.ProfissionalDto;
import com.barbearia.profissional.service.ProfissionalService;
import com.barbearia.servico.dto.ServicoDto;
import com.barbearia.servico.service.ServicoService;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * As 8 tools obrigatorias do PRD (Fase 10), cada uma so' um metodo Java
 * comum que chama servicos ja existentes — o LLM nunca decide preco,
 * disponibilidade ou grava nada sozinho, so pede a execucao de uma destas
 * tools e recebe o resultado (ver {@code AgenteAtendimentoService}).
 *
 * <p>UUIDs, nunca ids internos: {@code servicoUuids}/{@code profissionalUuid}
 * sao os mesmos uuids publicos usados em toda a API — consultar_servicos e
 * consultar_profissionais devolvem os uuids que as demais tools esperam
 * receber de volta.
 *
 * <p><strong>O cliente nunca vem de argumento de tool.</strong> Nenhuma tool
 * aceita telefone ou clienteUuid como parametro — todas usam sempre
 * {@code conversa.getCliente()}, o cliente que o backend ja resolveu a
 * partir do numero real do remetente do webhook ({@code
 * MensageriaInboundService}). Se o LLM pudesse informar esses dados (vindos,
 * em ultima instancia, do texto livre do "cliente" na conversa), qualquer
 * pessoa poderia se passar por outro cliente so' declarando o telefone ou
 * uuid dele, vazando nome/assinatura/agendamentos de terceiros ou ate'
 * criando agendamento em nome de outra pessoa.
 */
@Component
@RequiredArgsConstructor
public class AgenteTools {

    private final ServicoService servicoService;
    private final ProfissionalService profissionalService;
    private final AvailabilityService availabilityService;
    private final AgendamentoService agendamentoService;
    private final ClienteRepository clienteRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final ConversaRepository conversaRepository;
    private final AuditoriaService auditoriaService;
    private final BarbeariaRepository barbeariaRepository;
    private final ObjectMapper objectMapper;

    public List<DefinicaoFerramentaIa> definicoes() {
        return List.of(
                new DefinicaoFerramentaIa("consultar_servicos",
                        "Lista os servicos ativos da barbearia, com preco e duracao em minutos.",
                        Map.of(), List.of()),
                new DefinicaoFerramentaIa("consultar_profissionais",
                        "Lista os profissionais (barbeiros) ativos da barbearia.",
                        Map.of(), List.of()),
                new DefinicaoFerramentaIa("consultar_disponibilidade",
                        "Consulta os horarios realmente livres para um conjunto de servicos numa data, "
                                + "opcionalmente com um profissional especifico. SEMPRE chame esta tool antes de "
                                + "oferecer qualquer horario ao cliente — nunca invente ou presuma disponibilidade.",
                        Map.of(
                                "data", tipo("string", "Data desejada, formato AAAA-MM-DD."),
                                "servicoUuids", array("string",
                                        "Uuids dos servicos desejados (retornados por consultar_servicos)."),
                                "profissionalUuid", tipo("string",
                                        "Uuid do profissional preferido (retornado por consultar_profissionais). "
                                                + "Omita se o cliente nao tiver preferencia.")),
                        List.of("data", "servicoUuids")),
                new DefinicaoFerramentaIa("identificar_cliente",
                        "Identifica o cliente desta conversa (o telefone real ja e' conhecido pelo canal, "
                                + "nao precisa perguntar nem informar). Devolve novo=true se ainda nao ha cadastro "
                                + "com nome (nesse caso, pergunte o nome e chame cadastrar_cliente). Se o cliente "
                                + "tiver assinatura ativa, tambem devolve o saldo de cortes restantes no mes.",
                        Map.of(), List.of()),
                new DefinicaoFerramentaIa("cadastrar_cliente",
                        "Cadastra o nome do cliente novo desta conversa (o telefone real ja e' conhecido pelo "
                                + "canal, nao precisa informar).",
                        Map.of("nome", tipo("string", "Nome do cliente.")),
                        List.of("nome")),
                new DefinicaoFerramentaIa("criar_agendamento",
                        "Cria o agendamento de verdade para o cliente desta conversa, com todas as validacoes de "
                                + "conflito e antecedencia. So chame depois que o cliente confirmar explicitamente "
                                + "o resumo (servico, profissional, data, horario e valor).",
                        Map.of(
                                "profissionalUuid", tipo("string", "Uuid do profissional escolhido."),
                                "servicoUuids", array("string", "Uuids dos servicos escolhidos."),
                                "inicio", tipo("string", "Instante de inicio do agendamento, formato ISO-8601 (ex.: 2026-09-01T14:00:00-03:00).")),
                        List.of("profissionalUuid", "servicoUuids", "inicio")),
                new DefinicaoFerramentaIa("consultar_agendamentos_do_cliente",
                        "Lista os agendamentos (passados e futuros) do cliente desta conversa — util para o "
                                + "cliente recorrente que quer repetir o ultimo servico, ou para conferir "
                                + "agendamentos futuros.",
                        Map.of(), List.of()),
                new DefinicaoFerramentaIa("cancelar_agendamento",
                        "Cancela um agendamento futuro do cliente desta conversa. Se o cliente tiver mais de um "
                                + "agendamento futuro, pergunte qual antes de chamar (use "
                                + "consultar_agendamentos_do_cliente). So chame depois que o cliente confirmar "
                                + "explicitamente. Se o horario ja estiver muito proximo (fora da politica de "
                                + "cancelamento), a tool nao cancela e escala a conversa para atendimento humano — "
                                + "avise o cliente que alguem vai dar continuidade, sem recusar secamente.",
                        Map.of(
                                "agendamentoUuid", tipo("string",
                                        "Uuid do agendamento a cancelar (retornado por consultar_agendamentos_do_cliente)."),
                                "motivo", tipo("string", "Motivo do cancelamento, nas palavras do cliente.")),
                        List.of("agendamentoUuid", "motivo")),
                new DefinicaoFerramentaIa("remarcar_agendamento",
                        "Remarca um agendamento futuro do cliente desta conversa para um novo horario, mantendo o "
                                + "mesmo profissional e servicos. Sempre chame consultar_disponibilidade antes para "
                                + "confirmar que o novo horario esta realmente livre. Se o cliente tiver mais de um "
                                + "agendamento futuro, pergunte qual antes de chamar. So chame depois que o cliente "
                                + "confirmar explicitamente o novo horario.",
                        Map.of(
                                "agendamentoUuid", tipo("string",
                                        "Uuid do agendamento a remarcar (retornado por consultar_agendamentos_do_cliente)."),
                                "novoInicio", tipo("string",
                                        "Novo instante de inicio, formato ISO-8601 (ex.: 2026-09-01T14:00:00-03:00).")),
                        List.of("agendamentoUuid", "novoInicio")),
                new DefinicaoFerramentaIa("escalar_para_humano",
                        "Encerra o atendimento automatico e transfere a conversa para um atendente humano. Use em "
                                + "reclamacoes, pedido de desconto, assunto fora do escopo de agendamento, ou apos "
                                + "a terceira tentativa fracassada de entender o cliente.",
                        Map.of("motivo", tipo("string", "Motivo do escalonamento, em poucas palavras.")),
                        List.of("motivo")));
    }

    public ResultadoFerramenta executar(ChamadaFerramenta chamada, Conversa conversa) {
        try {
            Object resultado = switch (chamada.nome()) {
                case "consultar_servicos" -> consultarServicos();
                case "consultar_profissionais" -> consultarProfissionais();
                case "consultar_disponibilidade" -> consultarDisponibilidade(chamada.entrada());
                case "identificar_cliente" -> identificarCliente(conversa);
                case "cadastrar_cliente" -> cadastrarCliente(chamada.entrada(), conversa);
                case "criar_agendamento" -> criarAgendamento(chamada.entrada(), conversa);
                case "consultar_agendamentos_do_cliente" -> consultarAgendamentosDoCliente(conversa);
                case "cancelar_agendamento" -> cancelarAgendamento(chamada.entrada(), conversa);
                case "remarcar_agendamento" -> remarcarAgendamento(chamada.entrada(), conversa);
                case "escalar_para_humano" -> escalarParaHumano(chamada.entrada(), conversa);
                default -> throw new NegocioException("Ferramenta desconhecida: " + chamada.nome());
            };
            return new ResultadoFerramenta(chamada.id(), escrever(resultado), false);
        } catch (NegocioException | RecursoNaoEncontradoException e) {
            return new ResultadoFerramenta(chamada.id(), e.getMessage(), true);
        }
    }

    private List<Map<String, Object>> consultarServicos() {
        return servicoService.listar(null, null, true, Pageable.unpaged()).getContent().stream()
                .map(this::paraMapa)
                .toList();
    }

    private Map<String, Object> paraMapa(ServicoDto s) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("uuid", s.uuid());
        mapa.put("nome", s.nome());
        mapa.put("preco", s.preco());
        mapa.put("duracaoMinutos", s.duracaoMinutos());
        return mapa;
    }

    private List<Map<String, Object>> consultarProfissionais() {
        return profissionalService.listar(null, true, Pageable.unpaged()).getContent().stream()
                .map(this::paraMapa)
                .toList();
    }

    private Map<String, Object> paraMapa(ProfissionalDto p) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("uuid", p.uuid());
        mapa.put("nome", p.nome());
        return mapa;
    }

    @SuppressWarnings("unchecked")
    private List<SlotDisponivelDto> consultarDisponibilidade(Map<String, Object> entrada) {
        LocalDate data = LocalDate.parse((String) entrada.get("data"));
        List<UUID> servicoUuids = ((List<String>) entrada.get("servicoUuids")).stream().map(UUID::fromString)
                .toList();
        UUID profissionalUuid = entrada.get("profissionalUuid") != null
                ? UUID.fromString((String) entrada.get("profissionalUuid"))
                : null;
        return availabilityService.consultarDisponibilidade(data, servicoUuids, profissionalUuid);
    }

    /**
     * O cliente da tool e' sempre {@code conversa.getCliente()} — resolvido pelo backend a partir do
     * remetente real do webhook ({@code MensageriaInboundService}), nunca de um telefone/uuid que o
     * LLM tenha recebido do texto do cliente. Aceitar telefone/clienteUuid como argumento de tool
     * permitiria que qualquer pessoa na conversa se passasse por outro cliente (nome, assinatura,
     * agendamentos, ou ate' criar agendamento em nome de terceiros) so' declarando o dado errado.
     */
    private Map<String, Object> identificarCliente(Conversa conversa) {
        Cliente cliente = conversa.getCliente();

        Map<String, Object> resultado = new LinkedHashMap<>();
        if (eRascunho(cliente)) {
            resultado.put("novo", true);
            return resultado;
        }

        resultado.put("novo", false);
        resultado.put("clienteUuid", cliente.getUuidPublico());
        resultado.put("nome", cliente.getNome());
        assinaturaRepository.findByCliente_IdAndStatus(cliente.getId(), StatusAssinatura.ATIVA)
                .ifPresent(assinatura -> {
                    resultado.put("assinaturaAtiva", true);
                    resultado.put("planoNome", assinatura.getPlano().getNome());
                    resultado.put("saldoCortesRestantesNoMes", assinatura.getSaldoCortesAtual());
                });
        return resultado;
    }

    private boolean eRascunho(Cliente cliente) {
        return cliente.getOrigemCadastro() == OrigemCadastro.WHATSAPP
                && cliente.getNome().equals("Cliente " + cliente.getTelefone());
    }

    private Map<String, Object> cadastrarCliente(Map<String, Object> entrada, Conversa conversa) {
        String nome = (String) entrada.get("nome");

        Cliente cliente = conversa.getCliente();
        cliente.setNome(nome);
        cliente = clienteRepository.save(cliente);

        auditoriaService.registrar(null, "CLIENTE_NOMEADO_VIA_IA", "cliente", cliente.getId(),
                "Cliente '" + nome + "' identificado pelo agente de IA via WhatsApp", null);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("clienteUuid", cliente.getUuidPublico());
        resultado.put("nome", cliente.getNome());
        return resultado;
    }

    @SuppressWarnings("unchecked")
    private AgendamentoDto criarAgendamento(Map<String, Object> entrada, Conversa conversa) {
        UUID clienteUuid = conversa.getCliente().getUuidPublico();
        UUID profissionalUuid = UUID.fromString((String) entrada.get("profissionalUuid"));
        List<UUID> servicoUuids = ((List<String>) entrada.get("servicoUuids")).stream().map(UUID::fromString)
                .toList();
        Instant inicio = Instant.parse((String) entrada.get("inicio"));

        SalvarAgendamentoRequest request = new SalvarAgendamentoRequest(clienteUuid, profissionalUuid, servicoUuids,
                inicio, "Agendado pelo agente de IA via WhatsApp");
        AgendamentoDto criado = agendamentoService.criar(request, OrigemAgendamento.WHATSAPP, null, null);
        // O cliente ja confirmou explicitamente pelo chat (guardrail do prompt) — equivalente a
        // recepcao confirmar um agendamento no painel, entao ja confirma aqui, o que tambem
        // enfileira a sincronizacao com o Google Calendar (AgendamentoService.confirmar).
        return agendamentoService.confirmar(criado.uuid(), null, null);
    }

    private List<AgendamentoDto> consultarAgendamentosDoCliente(Conversa conversa) {
        return agendamentoService.listarPorCliente(conversa.getCliente().getUuidPublico());
    }

    /**
     * Mesmo guardrail de posse da criacao de agendamento: {@code agendamentoUuid} vem da tool call
     * do LLM, entao so' pode operar sobre um agendamento que realmente pertence ao cliente desta
     * conversa. "Agendamento nao encontrado" tanto para um uuid inexistente quanto para um uuid de
     * outro cliente — nao da' pra' distinguir os dois casos pra' quem esta' do outro lado do chat.
     */
    private Map<String, Object> cancelarAgendamento(Map<String, Object> entrada, Conversa conversa) {
        AgendamentoDto agendamento = buscarAgendamentoDoCliente((String) entrada.get("agendamentoUuid"), conversa);
        String motivo = (String) entrada.get("motivo");

        int antecedenciaMinimaMinutos = buscarBarbearia().getAntecedenciaMinimaCancelamentoMinutos();
        long minutosAteInicio = Duration.between(Instant.now(), agendamento.inicio()).toMinutes();
        if (minutosAteInicio < antecedenciaMinimaMinutos) {
            return escalar("Cliente pediu cancelamento fora da politica (menos de " + antecedenciaMinimaMinutos
                    + " min de antecedencia) via WhatsApp. Motivo informado: " + motivo, conversa);
        }

        agendamentoService.cancelar(agendamento.uuid(),
                new CancelarAgendamentoRequest("Cancelado pelo agente de IA via WhatsApp: " + motivo), null, null);

        auditoriaService.registrar(null, "AGENDAMENTO_CANCELADO_VIA_IA", "agendamento", null,
                "Agendamento " + agendamento.uuid() + " cancelado pelo agente de IA via WhatsApp. Motivo: " + motivo,
                null);

        return Map.of("cancelado", true);
    }

    private AgendamentoDto remarcarAgendamento(Map<String, Object> entrada, Conversa conversa) {
        AgendamentoDto atual = buscarAgendamentoDoCliente((String) entrada.get("agendamentoUuid"), conversa);
        Instant novoInicio = Instant.parse((String) entrada.get("novoInicio"));

        List<UUID> servicoUuids = atual.servicos().stream().map(AgendamentoServicoDto::servicoUuid).toList();
        SalvarAgendamentoRequest request = new SalvarAgendamentoRequest(atual.clienteUuid(),
                atual.profissionalUuid(), servicoUuids, novoInicio, atual.observacao());
        AgendamentoDto atualizado = agendamentoService.alterar(atual.uuid(), request, null, null);

        auditoriaService.registrar(null, "AGENDAMENTO_REMARCADO_VIA_IA", "agendamento", null,
                "Agendamento " + atual.uuid() + " remarcado para " + novoInicio + " pelo agente de IA via WhatsApp",
                null);

        return atualizado;
    }

    private AgendamentoDto buscarAgendamentoDoCliente(String agendamentoUuidBruto, Conversa conversa) {
        UUID agendamentoUuid = UUID.fromString(agendamentoUuidBruto);
        AgendamentoDto agendamento = agendamentoService.obter(agendamentoUuid);
        if (!agendamento.clienteUuid().equals(conversa.getCliente().getUuidPublico())) {
            throw new RecursoNaoEncontradoException("Agendamento nao encontrado.");
        }
        return agendamento;
    }

    private Barbearia buscarBarbearia() {
        return barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
    }

    private Map<String, Object> escalarParaHumano(Map<String, Object> entrada, Conversa conversa) {
        return escalar((String) entrada.get("motivo"), conversa);
    }

    private Map<String, Object> escalar(String motivo, Conversa conversa) {
        conversa.setModoAtendimento(ModoAtendimento.HUMANO);
        conversa.setMotivoEscalonamento(motivo);
        conversaRepository.save(conversa);
        return Map.of("escalado", true);
    }

    private Map<String, Object> tipo(String tipo, String descricao) {
        return Map.of("type", tipo, "description", descricao);
    }

    private Map<String, Object> array(String tipoDoItem, String descricao) {
        return Map.of("type", "array", "items", Map.of("type", tipoDoItem), "description", descricao);
    }

    private String escrever(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar resultado de tool para o agente de IA.", e);
        }
    }
}
