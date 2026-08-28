package com.barbearia.autoagendamento.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.agenda.domain.OrigemAgendamento;
import com.barbearia.agenda.dto.AgendamentoDto;
import com.barbearia.agenda.dto.AgendamentoServicoDto;
import com.barbearia.agenda.dto.SalvarAgendamentoRequest;
import com.barbearia.agenda.dto.SlotDisponivelDto;
import com.barbearia.agenda.service.AgendamentoService;
import com.barbearia.agenda.service.AvailabilityService;
import com.barbearia.autoagendamento.dto.ConfiguracaoAutoagendamentoDto;
import com.barbearia.autoagendamento.dto.CriarAutoagendamentoRequest;
import com.barbearia.autoagendamento.dto.ProfissionalPublicoDto;
import com.barbearia.autoagendamento.dto.ServicoPublicoDto;
import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.cliente.domain.Cliente;
import com.barbearia.cliente.dto.SalvarClienteRequest;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.cliente.service.ClienteService;
import com.barbearia.fiscal.email.EmailGateway;
import com.barbearia.profissional.dto.ProfissionalDto;
import com.barbearia.profissional.service.ProfissionalService;
import com.barbearia.servico.dto.ServicoDto;
import com.barbearia.servico.service.ServicoService;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;
import com.barbearia.shared.validacao.TelefoneNormalizador;

/**
 * Fase 9 (link de autoagendamento): reaproveita os mesmos servicos de
 * dominio ja usados pelo painel — nao ha nenhuma regra de negocio nova aqui,
 * so uma fronteira publica (sem autenticacao, ver {@code SecurityConfig})
 * que devolve projecoes seguras pro anonimo (sem e-mail/telefone/comissao de
 * profissional, ver {@link ProfissionalPublicoDto}).
 */
@Service
@RequiredArgsConstructor
public class AutoagendamentoService {

    private final BarbeariaRepository barbeariaRepository;
    private final ServicoService servicoService;
    private final ProfissionalService profissionalService;
    private final AvailabilityService availabilityService;
    private final AgendamentoService agendamentoService;
    private final ClienteRepository clienteRepository;
    private final ClienteService clienteService;
    private final EmailGateway emailGateway;

    @Transactional(readOnly = true)
    public ConfiguracaoAutoagendamentoDto obterConfiguracao() {
        Barbearia barbearia = buscarBarbearia();
        return new ConfiguracaoAutoagendamentoDto(barbearia.isPortalAutoagendamentoAtivo(), barbearia.getNome());
    }

    @Transactional(readOnly = true)
    public List<ServicoPublicoDto> consultarServicos() {
        garantirAtivo();
        return servicoService.listar(null, null, true, Pageable.unpaged()).getContent().stream()
                .map(this::paraPublico)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProfissionalPublicoDto> consultarProfissionais() {
        garantirAtivo();
        return profissionalService.listar(null, true, Pageable.unpaged()).getContent().stream()
                .map(this::paraPublico)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SlotDisponivelDto> consultarDisponibilidade(LocalDate data, List<UUID> servicoUuids,
            UUID profissionalUuid) {
        garantirAtivo();
        return availabilityService.consultarDisponibilidade(data, servicoUuids, profissionalUuid);
    }

    @Transactional
    public AgendamentoDto agendar(CriarAutoagendamentoRequest requisicao, HttpServletRequest httpRequest) {
        garantirAtivo();

        String telefone = normalizarTelefone(requisicao.telefone());
        Cliente cliente = clienteRepository.findByTelefone(telefone)
                .orElseGet(() -> criarCliente(requisicao, telefone, httpRequest));

        SalvarAgendamentoRequest agendamentoRequest = new SalvarAgendamentoRequest(cliente.getUuidPublico(),
                requisicao.profissionalUuid(), requisicao.servicoUuids(), requisicao.inicio(),
                "Agendado pelo link de autoagendamento publico");
        AgendamentoDto criado = agendamentoService.criar(agendamentoRequest, OrigemAgendamento.PORTAL, null,
                httpRequest);
        // O cliente ja confirmou no proprio fluxo do link (ultimo passo do wizard),
        // entao ja confirma aqui — o que tambem enfileira a sincronizacao com o
        // Google Calendar (AgendamentoService.confirmar).
        AgendamentoDto confirmado = agendamentoService.confirmar(criado.uuid(), null, httpRequest);

        enviarConfirmacoes(cliente, requisicao.email(), confirmado);

        return confirmado;
    }

    private Cliente criarCliente(CriarAutoagendamentoRequest requisicao, String telefone,
            HttpServletRequest httpRequest) {
        SalvarClienteRequest clienteRequest = new SalvarClienteRequest(requisicao.nome(), telefone, null, null,
                requisicao.email(), null, null, null, null, null, null, null, null, null, true,
                requisicao.consentimentoLgpd());
        clienteService.criar(clienteRequest, null, httpRequest);
        // criar() devolve um ClienteDto (uuid publico), mas o resto do fluxo precisa da entidade
        // Cliente completa — o telefone normalizado e' unico, entao buscar de volta resolve.
        return clienteRepository.findByTelefone(telefone)
                .orElseThrow(() -> new IllegalStateException("Cliente recem-criado nao encontrado."));
    }

    private void enviarConfirmacoes(Cliente cliente, String email, AgendamentoDto agendamento) {
        if (email != null && !email.isBlank()) {
            String resumo = montarResumo(agendamento);
            emailGateway.enviarConfirmacaoAgendamento(email, cliente.getNome(), resumo);
        }
    }

    private String montarResumo(AgendamentoDto agendamento) {
        ZoneId fuso = ZoneId.of(buscarBarbearia().getFusoHorario());
        DateTimeFormatter formatador = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.of("pt", "BR")).withZone(fuso);
        String servicos = agendamento.servicos().stream().map(AgendamentoServicoDto::nome)
                .reduce((a, b) -> a + ", " + b).orElse("");
        BigDecimal valor = agendamento.valorTotal();

        return "Agendamento confirmado! " + servicos + " com " + agendamento.profissionalNome() + " em "
                + formatador.format(agendamento.inicio()) + ". Valor: R$ " + valor + ".";
    }

    private ServicoPublicoDto paraPublico(ServicoDto s) {
        return new ServicoPublicoDto(s.uuid(), s.nome(), s.descricao(), s.categoria(), s.preco(), s.duracaoMinutos());
    }

    private ProfissionalPublicoDto paraPublico(ProfissionalDto p) {
        return new ProfissionalPublicoDto(p.uuid(), p.nome(), p.corAgenda());
    }

    private String normalizarTelefone(String bruto) {
        try {
            return TelefoneNormalizador.normalizar(bruto);
        } catch (IllegalArgumentException e) {
            throw new NegocioException("Telefone invalido.");
        }
    }

    private void garantirAtivo() {
        if (!buscarBarbearia().isPortalAutoagendamentoAtivo()) {
            throw new RecursoNaoEncontradoException("Link de autoagendamento indisponivel no momento.");
        }
    }

    private Barbearia buscarBarbearia() {
        return barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
    }
}
