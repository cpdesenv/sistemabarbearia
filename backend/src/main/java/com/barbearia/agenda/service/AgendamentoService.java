package com.barbearia.agenda.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.agenda.domain.AgendamentoServico;
import com.barbearia.agenda.domain.OrigemAgendamento;
import com.barbearia.agenda.domain.StatusAgendamento;
import com.barbearia.agenda.dto.AgendamentoDto;
import com.barbearia.agenda.dto.AgendamentoServicoDto;
import com.barbearia.agenda.dto.CancelarAgendamentoRequest;
import com.barbearia.agenda.dto.SalvarAgendamentoRequest;
import com.barbearia.agenda.exception.ConflitoAgendamentoException;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.agenda.repository.AgendamentoSpecifications;
import com.barbearia.cliente.domain.Cliente;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.profissional.domain.Profissional;
import com.barbearia.servico.domain.Servico;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

@Service
@RequiredArgsConstructor
public class AgendamentoService {

    private static final Set<StatusAgendamento> PODE_REMARCAR = Set.of(StatusAgendamento.AGENDADO,
            StatusAgendamento.CONFIRMADO);
    private static final Set<StatusAgendamento> PODE_CANCELAR = Set.of(StatusAgendamento.AGENDADO,
            StatusAgendamento.CONFIRMADO, StatusAgendamento.EM_ATENDIMENTO);
    private static final Set<StatusAgendamento> PODE_MARCAR_NAO_COMPARECEU = Set.of(StatusAgendamento.AGENDADO,
            StatusAgendamento.CONFIRMADO);

    private final AgendamentoRepository agendamentoRepository;
    private final ClienteRepository clienteRepository;
    private final AvailabilityService availabilityService;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<AgendamentoDto> listar(Instant de, Instant ate, UUID profissionalUuid, UUID clienteUuid,
            StatusAgendamento status) {
        return agendamentoRepository
                .findAll(AgendamentoSpecifications.comFiltros(de, ate, profissionalUuid, clienteUuid, status),
                        Sort.by("inicio"))
                .stream()
                .map(this::paraDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgendamentoDto obter(UUID uuid) {
        return paraDto(buscarPorUuid(uuid));
    }

    @Transactional
    public AgendamentoDto criar(SalvarAgendamentoRequest requisicao, Long usuarioId, HttpServletRequest httpRequest) {
        Cliente cliente = buscarCliente(requisicao.clienteUuid());
        List<Servico> servicos = availabilityService.resolverServicosAtivos(requisicao.servicoUuids());
        Profissional profissional = availabilityService.resolverProfissionalCapaz(requisicao.profissionalUuid(),
                servicos);

        Instant inicio = requisicao.inicio();
        Instant fim = calcularFim(inicio, servicos);
        availabilityService.validarSlotParaAgendamento(profissional, inicio, fim, null);

        Agendamento agendamento = new Agendamento();
        agendamento.setCliente(cliente);
        agendamento.setProfissional(profissional);
        agendamento.setInicio(inicio);
        agendamento.setFim(fim);
        agendamento.setOrigem(OrigemAgendamento.PAINEL);
        agendamento.setObservacao(requisicao.observacao());
        agendamento.setUsuarioCriadorId(usuarioId);
        aplicarServicos(agendamento, servicos);

        agendamento = salvarComTratamentoDeConflito(agendamento);

        auditoriaService.registrar(usuarioId, "AGENDAMENTO_CRIADO", "agendamento", agendamento.getId(),
                "Agendamento de '" + cliente.getNome() + "' com '" + profissional.getNome() + "' criado",
                httpRequest);

        return paraDto(agendamento);
    }

    @Transactional
    public AgendamentoDto alterar(UUID uuid, SalvarAgendamentoRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Agendamento agendamento = buscarPorUuid(uuid);
        if (!PODE_REMARCAR.contains(agendamento.getStatus())) {
            throw new NegocioException(
                    "Agendamentos com status '" + agendamento.getStatus() + "' nao podem ser remarcados.");
        }

        Cliente cliente = buscarCliente(requisicao.clienteUuid());
        List<Servico> servicos = availabilityService.resolverServicosAtivos(requisicao.servicoUuids());
        Profissional profissional = availabilityService.resolverProfissionalCapaz(requisicao.profissionalUuid(),
                servicos);

        Instant inicio = requisicao.inicio();
        Instant fim = calcularFim(inicio, servicos);
        availabilityService.validarSlotParaAgendamento(profissional, inicio, fim, agendamento.getId());

        agendamento.setCliente(cliente);
        agendamento.setProfissional(profissional);
        agendamento.setInicio(inicio);
        agendamento.setFim(fim);
        agendamento.setObservacao(requisicao.observacao());
        agendamento.limparServicos();
        aplicarServicos(agendamento, servicos);

        agendamento = salvarComTratamentoDeConflito(agendamento);

        auditoriaService.registrar(usuarioId, "AGENDAMENTO_REMARCADO", "agendamento", agendamento.getId(),
                "Agendamento remarcado para " + inicio + " com '" + profissional.getNome() + "'", httpRequest);

        return paraDto(agendamento);
    }

    @Transactional
    public AgendamentoDto confirmar(UUID uuid, Long usuarioId, HttpServletRequest httpRequest) {
        Agendamento agendamento = buscarPorUuid(uuid);
        transicionar(agendamento, Set.of(StatusAgendamento.AGENDADO), StatusAgendamento.CONFIRMADO);
        agendamento = agendamentoRepository.save(agendamento);

        auditoriaService.registrar(usuarioId, "AGENDAMENTO_CONFIRMADO", "agendamento", agendamento.getId(),
                "Agendamento confirmado", httpRequest);

        return paraDto(agendamento);
    }

    @Transactional
    public AgendamentoDto iniciarAtendimento(UUID uuid, Long usuarioId, HttpServletRequest httpRequest) {
        Agendamento agendamento = buscarPorUuid(uuid);
        transicionar(agendamento, Set.of(StatusAgendamento.CONFIRMADO), StatusAgendamento.EM_ATENDIMENTO);
        agendamento = agendamentoRepository.save(agendamento);

        auditoriaService.registrar(usuarioId, "AGENDAMENTO_ATENDIMENTO_INICIADO", "agendamento", agendamento.getId(),
                "Atendimento iniciado", httpRequest);

        return paraDto(agendamento);
    }

    @Transactional
    public AgendamentoDto finalizar(UUID uuid, Long usuarioId, HttpServletRequest httpRequest) {
        Agendamento agendamento = buscarPorUuid(uuid);
        transicionar(agendamento, Set.of(StatusAgendamento.EM_ATENDIMENTO), StatusAgendamento.FINALIZADO);
        agendamento = agendamentoRepository.save(agendamento);

        auditoriaService.registrar(usuarioId, "AGENDAMENTO_FINALIZADO", "agendamento", agendamento.getId(),
                "Atendimento finalizado", httpRequest);

        return paraDto(agendamento);
    }

    @Transactional
    public AgendamentoDto marcarNaoComparecimento(UUID uuid, Long usuarioId, HttpServletRequest httpRequest) {
        Agendamento agendamento = buscarPorUuid(uuid);
        if (agendamento.getInicio().isAfter(Instant.now())) {
            throw new NegocioException("Ainda nao chegou o horario deste agendamento.");
        }
        transicionar(agendamento, PODE_MARCAR_NAO_COMPARECEU, StatusAgendamento.NAO_COMPARECEU);
        agendamento = agendamentoRepository.save(agendamento);

        auditoriaService.registrar(usuarioId, "AGENDAMENTO_NAO_COMPARECEU", "agendamento", agendamento.getId(),
                "Cliente marcado como nao comparecido", httpRequest);

        return paraDto(agendamento);
    }

    @Transactional
    public AgendamentoDto cancelar(UUID uuid, CancelarAgendamentoRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Agendamento agendamento = buscarPorUuid(uuid);
        transicionar(agendamento, PODE_CANCELAR, StatusAgendamento.CANCELADO);
        agendamento.setMotivoCancelamento(requisicao.motivo());
        agendamento = agendamentoRepository.save(agendamento);

        auditoriaService.registrar(usuarioId, "AGENDAMENTO_CANCELADO", "agendamento", agendamento.getId(),
                "Agendamento cancelado. Motivo: " + requisicao.motivo(), httpRequest);

        return paraDto(agendamento);
    }

    private void transicionar(Agendamento agendamento, Set<StatusAgendamento> statusPermitidos,
            StatusAgendamento novoStatus) {
        if (!statusPermitidos.contains(agendamento.getStatus())) {
            throw new NegocioException("Nao e possivel mudar de '" + agendamento.getStatus() + "' para '"
                    + novoStatus + "'.");
        }
        agendamento.setStatus(novoStatus);
    }

    private Instant calcularFim(Instant inicio, List<Servico> servicos) {
        int duracaoTotal = servicos.stream().mapToInt(Servico::getDuracaoMinutos).sum();
        return inicio.plusSeconds(duracaoTotal * 60L);
    }

    private void aplicarServicos(Agendamento agendamento, List<Servico> servicos) {
        BigDecimal total = BigDecimal.ZERO;
        for (Servico servico : servicos) {
            agendamento.adicionarServico(new AgendamentoServico(servico, servico.getDuracaoMinutos(),
                    servico.getPreco()));
            total = total.add(servico.getPreco());
        }
        agendamento.setValorTotal(total);
    }

    /**
     * Salva forcando o flush imediato para que uma violacao da constraint de
     * exclusao do banco (dois agendamentos concorrentes disputando o mesmo
     * horario) seja capturada aqui, dentro da transacao, em vez de estourar
     * so' no commit.
     */
    private Agendamento salvarComTratamentoDeConflito(Agendamento agendamento) {
        try {
            return agendamentoRepository.saveAndFlush(agendamento);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflitoAgendamentoException(
                    "Este horario acabou de ser ocupado por outro agendamento. Escolha outro horario.");
        }
    }

    @Transactional(readOnly = true)
    public List<AgendamentoDto> listarPorCliente(UUID clienteUuidPublico) {
        return agendamentoRepository.findByCliente_UuidPublicoOrderByInicioDesc(clienteUuidPublico).stream()
                .map(this::paraDto)
                .toList();
    }

    private AgendamentoDto paraDto(Agendamento agendamento) {
        List<AgendamentoServicoDto> servicos = agendamento.getServicos().stream()
                .map(item -> new AgendamentoServicoDto(item.getServico().getUuidPublico(),
                        item.getServico().getNome(), item.getDuracaoMinutos(), item.getPreco()))
                .toList();

        return new AgendamentoDto(
                agendamento.getUuidPublico(),
                agendamento.getCliente().getUuidPublico(),
                agendamento.getCliente().getNome(),
                agendamento.getCliente().getTelefone(),
                agendamento.getProfissional().getUuidPublico(),
                agendamento.getProfissional().getNome(),
                agendamento.getProfissional().getCorAgenda(),
                servicos,
                agendamento.getInicio(),
                agendamento.getFim(),
                agendamento.getValorTotal(),
                agendamento.getStatus(),
                agendamento.getOrigem(),
                agendamento.getObservacao(),
                agendamento.getMotivoCancelamento(),
                agendamento.getCriadoEm(),
                agendamento.getAtualizadoEm());
    }

    private Cliente buscarCliente(UUID uuid) {
        return clienteRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente nao encontrado."));
    }

    private Agendamento buscarPorUuid(UUID uuid) {
        return agendamentoRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento nao encontrado."));
    }
}
