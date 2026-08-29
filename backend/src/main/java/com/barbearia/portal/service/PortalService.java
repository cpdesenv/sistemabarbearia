package com.barbearia.portal.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.agenda.domain.OrigemAgendamento;
import com.barbearia.agenda.dto.AgendamentoDto;
import com.barbearia.agenda.dto.SalvarAgendamentoRequest;
import com.barbearia.agenda.dto.SlotDisponivelDto;
import com.barbearia.agenda.service.AgendamentoService;
import com.barbearia.agenda.service.AvailabilityService;
import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.cliente.domain.Cliente;
import com.barbearia.cliente.domain.OrigemCadastro;
import com.barbearia.cliente.dto.ClienteDto;
import com.barbearia.cliente.dto.SalvarClienteRequest;
import com.barbearia.cliente.exception.ClienteDuplicadoException;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.cliente.service.ClienteService;
import com.barbearia.fiscal.email.EmailGateway;
import com.barbearia.portal.dto.PortalAgendamentoConfirmadoDto;
import com.barbearia.portal.dto.PortalAgendamentoRequest;
import com.barbearia.portal.dto.PortalAgendamentoServicoDto;
import com.barbearia.portal.dto.PortalProfissionalDto;
import com.barbearia.portal.dto.PortalServicoDto;
import com.barbearia.profissional.domain.Profissional;
import com.barbearia.servico.domain.Servico;
import com.barbearia.servico.repository.ServicoRepository;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;
import com.barbearia.shared.validacao.TelefoneNormalizador;

/**
 * Orquestra o autoagendamento publico (Fase 9): catalogo, disponibilidade e
 * criacao de agendamento ja CONFIRMADO, reaproveitando integralmente as
 * mesmas regras de negocio do painel (AvailabilityService/AgendamentoService)
 * — nada de logica de disponibilidade/conflito duplicada aqui.
 */
@Service
@RequiredArgsConstructor
public class PortalService {

    private static final Logger log = LoggerFactory.getLogger(PortalService.class);

    private final BarbeariaRepository barbeariaRepository;
    private final ServicoRepository servicoRepository;
    private final AvailabilityService availabilityService;
    private final ClienteRepository clienteRepository;
    private final ClienteService clienteService;
    private final AgendamentoService agendamentoService;
    private final EmailGateway emailGateway;

    @Transactional(readOnly = true)
    public boolean ativo() {
        return obterBarbearia().isPortalAgendamentoAtivo();
    }

    @Transactional(readOnly = true)
    public List<PortalServicoDto> listarServicos() {
        exigirPortalAtivo();
        return servicoRepository.findByAtivoTrueOrderByNomeAsc().stream()
                .map(s -> new PortalServicoDto(s.getUuidPublico(), s.getNome(), s.getDescricao(), s.getCategoria(),
                        s.getPreco(), s.getDuracaoMinutos()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PortalProfissionalDto> listarProfissionais(List<UUID> servicoUuids) {
        exigirPortalAtivo();
        List<Servico> servicos = availabilityService.resolverServicosAtivos(servicoUuids);
        List<Profissional> profissionais = availabilityService.resolverProfissionaisCapazes(null, servicos);
        return profissionais.stream()
                .map(p -> new PortalProfissionalDto(p.getUuidPublico(), p.getNome(), p.getCorAgenda()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SlotDisponivelDto> consultarDisponibilidade(LocalDate data, List<UUID> servicoUuids,
            UUID profissionalUuid) {
        exigirPortalAtivo();
        return availabilityService.consultarDisponibilidade(data, servicoUuids, profissionalUuid);
    }

    @Transactional
    public PortalAgendamentoConfirmadoDto criarAgendamento(PortalAgendamentoRequest requisicao,
            HttpServletRequest httpRequest) {
        exigirPortalAtivo();

        Cliente cliente = identificarOuCadastrarCliente(requisicao, httpRequest);

        SalvarAgendamentoRequest requisicaoAgendamento = new SalvarAgendamentoRequest(cliente.getUuidPublico(),
                requisicao.profissionalUuid(), requisicao.servicoUuids(), requisicao.inicio(), null);
        AgendamentoDto agendamento = agendamentoService.criar(requisicaoAgendamento, OrigemAgendamento.PORTAL, null,
                httpRequest);
        agendamento = agendamentoService.confirmar(agendamento.uuid(), null, httpRequest);

        enviarConfirmacaoPorEmailSeHouver(cliente, agendamento);

        return paraDtoPublico(requisicao, agendamento);
    }

    /**
     * Devolve o nome DIGITADO pelo solicitante (requisicao.nome()), nunca o
     * nome gravado no cadastro do cliente (agendamento.clienteNome()) — ver
     * javadoc de {@link PortalAgendamentoConfirmadoDto}. Tambem omite
     * clienteUuid/clienteTelefone, que nao tem por que ir a um chamador
     * anonimo.
     */
    private PortalAgendamentoConfirmadoDto paraDtoPublico(PortalAgendamentoRequest requisicao,
            AgendamentoDto agendamento) {
        List<PortalAgendamentoServicoDto> servicos = agendamento.servicos().stream()
                .map(s -> new PortalAgendamentoServicoDto(s.servicoUuid(), s.nome(), s.duracaoMinutos(), s.preco()))
                .toList();
        return new PortalAgendamentoConfirmadoDto(agendamento.uuid(), requisicao.nome(),
                agendamento.profissionalNome(), agendamento.profissionalCorAgenda(), servicos, agendamento.inicio(),
                agendamento.fim(), agendamento.valorTotal(), agendamento.status());
    }

    /**
     * Verifica primeiro se ja existe cliente com este telefone, em vez de so'
     * tentar criar e capturar {@link ClienteDuplicadoException}: como
     * {@code ClienteService.criar} roda na mesma transacao (propagacao
     * padrao), uma excecao lancada por ele marca a transacao inteira como
     * rollback-only antes mesmo de chegar ao catch aqui — o commit no final
     * de {@link #criarAgendamento} falharia com
     * {@code UnexpectedRollbackException} mesmo capturando a excecao. O
     * catch abaixo fica so' como rede de seguranca para a corrida rara de
     * duas requisicoes simultaneas com o mesmo telefone novo (reforcada por
     * {@code @Transactional(noRollbackFor = ClienteDuplicadoException.class)}
     * em {@code ClienteService.criar}).
     */
    private Cliente identificarOuCadastrarCliente(PortalAgendamentoRequest requisicao,
            HttpServletRequest httpRequest) {
        String telefoneNormalizado;
        try {
            telefoneNormalizado = TelefoneNormalizador.normalizar(requisicao.telefone());
        } catch (IllegalArgumentException ex) {
            throw new NegocioException("Telefone invalido.");
        }

        return clienteRepository.findByTelefone(telefoneNormalizado).orElseGet(() -> {
            SalvarClienteRequest requisicaoCliente = new SalvarClienteRequest(requisicao.nome(),
                    requisicao.telefone(), null, null, requisicao.email(), null, null, null, null, null, null, null,
                    null, null, false, true);
            try {
                ClienteDto clienteDto = clienteService.criar(requisicaoCliente, OrigemCadastro.PORTAL, null,
                        httpRequest);
                return clienteRepository.findByUuidPublico(clienteDto.uuid())
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente nao encontrado."));
            } catch (ClienteDuplicadoException ex) {
                return ex.getClienteExistente();
            }
        });
    }

    private void enviarConfirmacaoPorEmailSeHouver(Cliente cliente, AgendamentoDto agendamento) {
        String email = cliente.getEmail();
        if (email == null || email.isBlank()) {
            return;
        }
        try {
            String resumoServicos = agendamento.servicos().stream()
                    .map(s -> s.nome())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            emailGateway.enviarConfirmacaoAgendamento(email, cliente.getNome(), resumoServicos,
                    agendamento.profissionalNome(), agendamento.inicio(), agendamento.valorTotal());
        } catch (Exception e) {
            log.warn("Falha ao enviar confirmacao do agendamento {} por e-mail (agendamento ja esta confirmado).",
                    agendamento.uuid(), e);
        }
    }

    private void exigirPortalAtivo() {
        if (!obterBarbearia().isPortalAgendamentoAtivo()) {
            throw new RecursoNaoEncontradoException("Portal de agendamento nao esta disponivel no momento.");
        }
    }

    private Barbearia obterBarbearia() {
        return barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
    }
}
