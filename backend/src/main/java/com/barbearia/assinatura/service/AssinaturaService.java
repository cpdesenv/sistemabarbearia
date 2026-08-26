package com.barbearia.assinatura.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.assinatura.domain.Assinatura;
import com.barbearia.assinatura.domain.PlanoAssinatura;
import com.barbearia.assinatura.domain.StatusAssinatura;
import com.barbearia.assinatura.dto.AssinaturaDto;
import com.barbearia.assinatura.dto.AssinaturaResumoDto;
import com.barbearia.assinatura.dto.CancelarAssinaturaRequest;
import com.barbearia.assinatura.dto.CriarAssinaturaRequest;
import com.barbearia.assinatura.dto.RelatorioReceitaAssinaturaDto;
import com.barbearia.assinatura.repository.AssinaturaRepository;
import com.barbearia.assinatura.repository.PlanoAssinaturaRepository;
import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.cliente.domain.Cliente;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.financeiro.domain.StatusComanda;
import com.barbearia.financeiro.domain.StatusContaReceber;
import com.barbearia.financeiro.repository.ComandaRepository;
import com.barbearia.financeiro.repository.ContaReceberRepository;
import com.barbearia.financeiro.domain.ContaReceber;
import com.barbearia.servico.domain.Servico;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Clube Cavalinho: assinaturas de clientes a um {@link PlanoAssinatura}.
 *
 * <p>Nao ha gateway de pagamento no projeto — a mensalidade de cada ciclo e'
 * uma {@code ContaReceber} comum, recebida manualmente pela recepcao (mesmo
 * fluxo de "Contas a receber" ja existente). "Falha de cobranca" significa
 * que essa conta seguiu {@code PENDENTE} ate a data da proxima renovacao; o
 * "retry automatico" e' o proprio job diario ({@link #processarRenovacoes()})
 * tentando de novo a cada execucao, ate a conta ser recebida ou a assinatura
 * ser cancelada.
 */
@Service
@RequiredArgsConstructor
public class AssinaturaService {

    private final AssinaturaRepository assinaturaRepository;
    private final PlanoAssinaturaRepository planoAssinaturaRepository;
    private final ClienteRepository clienteRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final ComandaRepository comandaRepository;
    private final BarbeariaRepository barbeariaRepository;
    private final AuditoriaService auditoriaService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<AssinaturaDto> listar(StatusAssinatura status) {
        List<Assinatura> assinaturas = status != null
                ? assinaturaRepository.findByStatusOrderByCriadoEmDesc(status)
                : assinaturaRepository.findAllByOrderByCriadoEmDesc();
        return assinaturas.stream().map(this::paraDto).toList();
    }

    @Transactional(readOnly = true)
    public AssinaturaDto obter(UUID uuid) {
        return paraDto(buscarPorUuid(uuid));
    }

    @Transactional(readOnly = true)
    public AssinaturaResumoDto resumo() {
        long ativas = assinaturaRepository.countByStatus(StatusAssinatura.ATIVA);
        long inadimplentes = assinaturaRepository.countByStatus(StatusAssinatura.INADIMPLENTE);
        long suspensas = assinaturaRepository.countByStatus(StatusAssinatura.SUSPENSA);
        long canceladas = assinaturaRepository.countByStatus(StatusAssinatura.CANCELADA);
        BigDecimal receitaRecorrente = assinaturaRepository.somarPrecoMensalPorStatus(StatusAssinatura.ATIVA);
        return new AssinaturaResumoDto(ativas, inadimplentes, suspensas, canceladas, receitaRecorrente);
    }

    @Transactional(readOnly = true)
    public RelatorioReceitaAssinaturaDto relatorioReceita(YearMonth mesReferencia) {
        LocalDate inicio = mesReferencia.atDay(1);
        LocalDate fim = mesReferencia.atEndOfMonth();
        ZoneId fuso = fusoHorario();
        Instant inicioInstant = inicio.atStartOfDay(fuso).toInstant();
        Instant fimInstant = fim.plusDays(1).atStartOfDay(fuso).toInstant();

        BigDecimal receitaAssinaturas = contaReceberRepository
                .somarMensalidadesRecebidasNoPeriodo(StatusContaReceber.RECEBIDA, inicio, fim);
        BigDecimal receitaAvulsa = comandaRepository
                .somarValorTotalPorStatusEPeriodo(StatusComanda.FECHADA, inicioInstant, fimInstant);

        return new RelatorioReceitaAssinaturaDto(mesReferencia, receitaAssinaturas, receitaAvulsa);
    }

    @Transactional
    public AssinaturaDto assinar(CriarAssinaturaRequest requisicao, Long usuarioId, HttpServletRequest httpRequest) {
        Cliente cliente = clienteRepository.findByUuidPublico(requisicao.clienteUuid())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente nao encontrado."));
        PlanoAssinatura plano = planoAssinaturaRepository.findByUuidPublico(requisicao.planoUuid())
                .filter(PlanoAssinatura::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Plano de assinatura nao encontrado ou inativo."));

        boolean jaTemAssinaturaEmCurso = assinaturaRepository
                .findByCliente_IdAndStatus(cliente.getId(), StatusAssinatura.ATIVA).isPresent()
                || assinaturaRepository.findByCliente_IdAndStatus(cliente.getId(), StatusAssinatura.INADIMPLENTE)
                        .isPresent();
        if (jaTemAssinaturaEmCurso) {
            throw new NegocioException("Este cliente ja possui uma assinatura em curso.");
        }

        LocalDate hoje = hoje();
        Assinatura assinatura = new Assinatura();
        assinatura.setCliente(cliente);
        assinatura.setPlano(plano);
        assinatura.setStatus(StatusAssinatura.ATIVA);
        assinatura.setSaldoCortesAtual(plano.getCortesIncluidosPorCiclo());
        assinatura.setDataInicio(hoje);
        assinatura.setDataProximaRenovacao(hoje.plusMonths(1));

        try {
            assinatura = assinaturaRepository.saveAndFlush(assinatura);
        } catch (DataIntegrityViolationException ex) {
            throw new NegocioException("Este cliente ja possui uma assinatura em curso.");
        }

        contaReceberRepository.save(novaContaMensalidade(assinatura, hoje));

        auditoriaService.registrar(usuarioId, "ASSINATURA_CRIADA", "assinatura", assinatura.getId(),
                "Assinatura de '" + cliente.getNome() + "' ao plano '" + plano.getNome() + "' criada", httpRequest);

        return paraDto(assinatura);
    }

    @Transactional
    public AssinaturaDto cancelar(UUID uuid, CancelarAssinaturaRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Assinatura assinatura = buscarPorUuid(uuid);
        if (assinatura.getStatus() == StatusAssinatura.CANCELADA) {
            throw new NegocioException("Esta assinatura ja esta cancelada.");
        }

        assinatura.setStatus(StatusAssinatura.CANCELADA);
        assinatura.setDataCancelamento(requisicao.dataEfeito());
        assinatura.setMotivoCancelamento(requisicao.motivo());
        assinatura = assinaturaRepository.save(assinatura);

        auditoriaService.registrar(usuarioId, "ASSINATURA_CANCELADA", "assinatura", assinatura.getId(),
                "Assinatura de '" + assinatura.getCliente().getNome() + "' cancelada. Motivo: "
                        + requisicao.motivo(),
                httpRequest);

        return paraDto(assinatura);
    }

    /**
     * Tenta cobrir um servico pelo saldo da assinatura ATIVA do cliente
     * (chamado por {@code ComandaService} ao montar os itens da comanda).
     * So' consome saldo se o cliente tiver assinatura ATIVA cujo plano cubra
     * o servico E ainda houver saldo — ajuste atomico via
     * {@code AssinaturaRepository#ajustarSaldo}, para que dois agendamentos
     * simultaneos do mesmo cliente nunca consumam saldo em duplicidade.
     *
     * @return a assinatura que consumiu o saldo (para o item guardar a
     *         referencia e permitir devolucao exata), ou vazio se o cliente
     *         nao e assinante, o servico nao esta incluso no plano, ou o
     *         saldo esta zerado — nesses casos o item e' cobrado avulso.
     */
    @Transactional
    public Optional<Assinatura> tentarConsumirSaldo(Cliente cliente, Servico servico) {
        Optional<Assinatura> assinaturaAtivaOpt = assinaturaRepository
                .findByCliente_IdAndStatus(cliente.getId(), StatusAssinatura.ATIVA);
        if (assinaturaAtivaOpt.isEmpty() || !assinaturaAtivaOpt.get().getPlano().cobreServico(servico)) {
            return Optional.empty();
        }

        Assinatura assinaturaAtiva = assinaturaAtivaOpt.get();
        int linhasAfetadas = assinaturaRepository.ajustarSaldo(assinaturaAtiva.getId(), -1);
        if (linhasAfetadas == 0) {
            return Optional.empty();
        }
        entityManager.refresh(assinaturaAtiva);
        return Optional.of(assinaturaAtiva);
    }

    /** Devolve 1 corte ao saldo — chamado quando um item coberto e' removido da comanda ou a comanda e' estornada. */
    @Transactional
    public void devolverSaldo(Assinatura assinatura) {
        assinaturaRepository.ajustarSaldo(assinatura.getId(), 1);
    }

    /**
     * Job diario: renova cada assinatura ATIVA/INADIMPLENTE cuja data de
     * renovacao chegou. So' renova (reabastece saldo, gera a proxima
     * mensalidade) se a mensalidade do ciclo anterior ja foi recebida; caso
     * contrario marca INADIMPLENTE e tenta de novo no dia seguinte.
     */
    @Transactional
    public void processarRenovacoes() {
        LocalDate hoje = hoje();
        List<Assinatura> pendentes = assinaturaRepository.findByStatusInAndDataProximaRenovacaoLessThanEqual(
                List.of(StatusAssinatura.ATIVA, StatusAssinatura.INADIMPLENTE), hoje);
        for (Assinatura assinatura : pendentes) {
            processarRenovacaoIndividual(assinatura, hoje);
        }
    }

    private void processarRenovacaoIndividual(Assinatura assinatura, LocalDate hoje) {
        Optional<ContaReceber> ultimaCobranca = contaReceberRepository
                .findTopByAssinaturaOrderByDataVencimentoDesc(assinatura);
        boolean cicloAnteriorPago = ultimaCobranca.isEmpty()
                || ultimaCobranca.get().getStatus() == StatusContaReceber.RECEBIDA;

        if (!cicloAnteriorPago) {
            if (assinatura.getStatus() != StatusAssinatura.INADIMPLENTE) {
                assinatura.setStatus(StatusAssinatura.INADIMPLENTE);
                assinaturaRepository.save(assinatura);
                auditoriaService.registrar(null, "ASSINATURA_INADIMPLENTE", "assinatura", assinatura.getId(),
                        "Assinatura de '" + assinatura.getCliente().getNome()
                                + "' marcada como inadimplente: mensalidade do ciclo anterior nao foi recebida ate a"
                                + " data de renovacao.",
                        null);
            }
            return;
        }

        // Reseta (nao soma) o saldo para o valor cheio do ciclo — cortes nao usados no
        // ciclo anterior nao acumulam para o proximo. Atribuicao direta, sem o ajuste
        // atomico de AssinaturaRepository#ajustarSaldo: a renovacao roda uma vez por dia
        // por um unico job, sem a mesma concorrencia que o consumo de saldo por comanda.
        assinatura.setSaldoCortesAtual(assinatura.getPlano().getCortesIncluidosPorCiclo());

        if (assinatura.getStatus() == StatusAssinatura.INADIMPLENTE) {
            assinatura.setStatus(StatusAssinatura.ATIVA);
        }
        assinatura.setDataProximaRenovacao(assinatura.getDataProximaRenovacao().plusMonths(1));
        assinatura = assinaturaRepository.save(assinatura);

        contaReceberRepository.save(novaContaMensalidade(assinatura, hoje));

        auditoriaService.registrar(null, "ASSINATURA_RENOVADA", "assinatura", assinatura.getId(),
                "Assinatura de '" + assinatura.getCliente().getNome() + "' renovada. Novo saldo: "
                        + assinatura.getSaldoCortesAtual(),
                null);
    }

    private ContaReceber novaContaMensalidade(Assinatura assinatura, LocalDate vencimento) {
        ContaReceber conta = new ContaReceber();
        conta.setCliente(assinatura.getCliente());
        conta.setAssinatura(assinatura);
        conta.setDescricao("Mensalidade " + assinatura.getPlano().getNome() + " - " + vencimento.getMonthValue()
                + "/" + vencimento.getYear());
        conta.setValor(assinatura.getPlano().getPrecoMensal());
        conta.setDataVencimento(vencimento);
        return conta;
    }

    private AssinaturaDto paraDto(Assinatura assinatura) {
        return new AssinaturaDto(assinatura.getUuidPublico(), assinatura.getCliente().getUuidPublico(),
                assinatura.getCliente().getNome(), assinatura.getPlano().getUuidPublico(),
                assinatura.getPlano().getNome(), assinatura.getStatus(), assinatura.getSaldoCortesAtual(),
                assinatura.getDataInicio(), assinatura.getDataProximaRenovacao(), assinatura.getDataCancelamento(),
                assinatura.getMotivoCancelamento(), assinatura.getCriadoEm(), assinatura.getAtualizadoEm());
    }

    private Assinatura buscarPorUuid(UUID uuid) {
        return assinaturaRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Assinatura nao encontrada."));
    }

    private LocalDate hoje() {
        return LocalDate.now(fusoHorario());
    }

    private ZoneId fusoHorario() {
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
        return ZoneId.of(barbearia.getFusoHorario());
    }
}
