package com.barbearia.dashboard.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.agenda.domain.StatusAgendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.assinatura.domain.StatusAssinatura;
import com.barbearia.assinatura.repository.AssinaturaRepository;
import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.calendar.domain.StatusOutbox;
import com.barbearia.calendar.repository.AgendamentoCalendarOutboxRepository;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.dashboard.dto.DashboardCardsDto;
import com.barbearia.dashboard.dto.DashboardGraficosDto;
import com.barbearia.dashboard.dto.DashboardResumoDto;
import com.barbearia.dashboard.dto.IndicadoresAssinaturaDto;
import com.barbearia.dashboard.dto.IndicadoresSaudeDto;
import com.barbearia.dashboard.dto.ItemContagemDto;
import com.barbearia.dashboard.dto.PontoMensalDto;
import com.barbearia.financeiro.domain.StatusComanda;
import com.barbearia.financeiro.dto.TotalPorFormaPagamentoDto;
import com.barbearia.financeiro.repository.ComandaRepository;
import com.barbearia.horario.domain.JanelaHorario;
import com.barbearia.horario.repository.JanelaHorarioRepository;
import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.repository.ProfissionalRepository;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Agrega dados ja existentes em financeiro, agenda, produtos, assinaturas e
 * calendar num unico payload de leitura para a tela de dashboard (Fase 10) —
 * nao introduz novo estado, so' consultas.
 *
 * <p>Duas simplificacoes deliberadas de v1: {@code taxaOcupacaoHoje} nao
 * desconta {@code Bloqueio} da capacidade (so' considera a grade semanal),
 * e {@code taxaChurnMes} usa como base as assinaturas ATIVA/INADIMPLENTE
 * atuais mais as canceladas no mes, na falta de um snapshot historico de
 * quantas assinaturas estavam em curso no inicio do mes.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int MESES_HISTORICO_FATURAMENTO = 12;
    private static final int TOP_SERVICOS_MAIS_VENDIDOS = 5;

    private final BarbeariaRepository barbeariaRepository;
    private final ComandaRepository comandaRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ClienteRepository clienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final JanelaHorarioRepository janelaHorarioRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final AgendamentoCalendarOutboxRepository agendamentoCalendarOutboxRepository;

    @Transactional(readOnly = true)
    public DashboardResumoDto resumo() {
        Barbearia barbearia = obterBarbearia();
        ZoneId fuso = ZoneId.of(barbearia.getFusoHorario());
        LocalDate hoje = LocalDate.now(fuso);
        YearMonth mesAtual = YearMonth.from(hoje);

        Instant inicioHoje = hoje.atStartOfDay(fuso).toInstant();
        Instant fimHoje = hoje.plusDays(1).atStartOfDay(fuso).toInstant();
        Instant inicioMes = mesAtual.atDay(1).atStartOfDay(fuso).toInstant();
        Instant fimMes = mesAtual.plusMonths(1).atDay(1).atStartOfDay(fuso).toInstant();

        DashboardCardsDto cards = montarCards(fuso, hoje, inicioHoje, fimHoje, mesAtual, inicioMes, fimMes);
        IndicadoresSaudeDto indicadoresSaude = montarIndicadoresSaude(inicioMes, fimMes);
        IndicadoresAssinaturaDto indicadoresAssinatura = montarIndicadoresAssinatura(mesAtual);
        DashboardGraficosDto graficos = montarGraficos(fuso, mesAtual, inicioMes, fimMes);

        return new DashboardResumoDto(cards, indicadoresSaude, indicadoresAssinatura, graficos);
    }

    private DashboardCardsDto montarCards(ZoneId fuso, LocalDate hoje, Instant inicioHoje, Instant fimHoje,
            YearMonth mesAtual, Instant inicioMes, Instant fimMes) {
        BigDecimal faturamentoDia = comandaRepository.somarValorTotalPorStatusEPeriodo(StatusComanda.FECHADA,
                inicioHoje, fimHoje);
        BigDecimal faturamentoMes = comandaRepository.somarValorTotalPorStatusEPeriodo(StatusComanda.FECHADA,
                inicioMes, fimMes);

        YearMonth mesAnterior = mesAtual.minusMonths(1);
        Instant inicioMesAnterior = mesAnterior.atDay(1).atStartOfDay(fuso).toInstant();
        BigDecimal faturamentoMesAnterior = comandaRepository.somarValorTotalPorStatusEPeriodo(StatusComanda.FECHADA,
                inicioMesAnterior, inicioMes);

        BigDecimal percentualVsMesAnterior = null;
        if (faturamentoMesAnterior.compareTo(BigDecimal.ZERO) != 0) {
            percentualVsMesAnterior = faturamentoMes.subtract(faturamentoMesAnterior)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(faturamentoMesAnterior, 2, RoundingMode.HALF_UP);
        }

        long atendimentosDia = comandaRepository.contarPorStatusEPeriodo(StatusComanda.FECHADA, inicioHoje, fimHoje);
        BigDecimal ticketMedioDia = atendimentosDia == 0
                ? BigDecimal.ZERO
                : faturamentoDia.divide(BigDecimal.valueOf(atendimentosDia), 2, RoundingMode.HALF_UP);

        BigDecimal taxaOcupacaoHoje = calcularTaxaOcupacaoHoje(hoje, inicioHoje, fimHoje);

        return new DashboardCardsDto(faturamentoDia, faturamentoMes, percentualVsMesAnterior, atendimentosDia,
                ticketMedioDia, taxaOcupacaoHoje);
    }

    private BigDecimal calcularTaxaOcupacaoHoje(LocalDate hoje, Instant inicioHoje, Instant fimHoje) {
        int diaSemana = hoje.getDayOfWeek().getValue();
        List<Profissional> ativos = profissionalRepository.findByAtivoTrue();

        long minutosCapacidade = ativos.stream()
                .flatMap(profissional -> janelaHorarioRepository
                        .findByProfissionalAndDiaSemanaOrderByHoraInicioAsc(profissional, diaSemana).stream())
                .mapToLong(this::minutosDaJanela)
                .sum();

        List<Agendamento> agendamentosHoje = agendamentoRepository
                .findByInicioGreaterThanEqualAndInicioLessThanAndStatusNot(inicioHoje, fimHoje,
                        StatusAgendamento.CANCELADO);
        long minutosOcupados = agendamentosHoje.stream()
                .mapToLong(a -> Duration.between(a.getInicio(), a.getFim()).toMinutes())
                .sum();

        if (minutosCapacidade == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(minutosOcupados).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(minutosCapacidade), 2, RoundingMode.HALF_UP);
    }

    private long minutosDaJanela(JanelaHorario janela) {
        return Duration.between(janela.getHoraInicio(), janela.getHoraFim()).toMinutes();
    }

    private IndicadoresSaudeDto montarIndicadoresSaude(Instant inicioMes, Instant fimMes) {
        long clientesNovosMes = clienteRepository.countByCriadoEmBetween(inicioMes, fimMes);
        long cancelamentosMes = agendamentoRepository.countByStatusAndInicioBetween(StatusAgendamento.CANCELADO,
                inicioMes, fimMes);
        long faltasMes = agendamentoRepository.countByStatusAndInicioBetween(StatusAgendamento.NAO_COMPARECEU,
                inicioMes, fimMes);
        long agendamentosForaDeSincronia = agendamentoCalendarOutboxRepository
                .countByStatus(StatusOutbox.FALHA_PERMANENTE);

        return new IndicadoresSaudeDto(clientesNovosMes, cancelamentosMes, faltasMes, agendamentosForaDeSincronia);
    }

    private IndicadoresAssinaturaDto montarIndicadoresAssinatura(YearMonth mesAtual) {
        BigDecimal receitaRecorrente = assinaturaRepository.somarPrecoMensalPorStatus(StatusAssinatura.ATIVA);

        long canceladasNoMes = assinaturaRepository.countByStatusAndDataCancelamentoBetween(
                StatusAssinatura.CANCELADA, mesAtual.atDay(1), mesAtual.atEndOfMonth());
        long emCursoAtualmente = assinaturaRepository.countByStatus(StatusAssinatura.ATIVA)
                + assinaturaRepository.countByStatus(StatusAssinatura.INADIMPLENTE);
        long baseParaChurn = emCursoAtualmente + canceladasNoMes;

        BigDecimal taxaChurnMes = baseParaChurn == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(canceladasNoMes).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(baseParaChurn), 2, RoundingMode.HALF_UP);

        return new IndicadoresAssinaturaDto(receitaRecorrente, taxaChurnMes);
    }

    private DashboardGraficosDto montarGraficos(ZoneId fuso, YearMonth mesAtual, Instant inicioMes, Instant fimMes) {
        List<PontoMensalDto> faturamentoUltimos12Meses = new ArrayList<>();
        for (int i = MESES_HISTORICO_FATURAMENTO - 1; i >= 0; i--) {
            YearMonth mes = mesAtual.minusMonths(i);
            Instant inicioDoMes = mes.atDay(1).atStartOfDay(fuso).toInstant();
            Instant fimDoMes = mes.plusMonths(1).atDay(1).atStartOfDay(fuso).toInstant();
            BigDecimal valor = comandaRepository.somarValorTotalPorStatusEPeriodo(StatusComanda.FECHADA, inicioDoMes,
                    fimDoMes);
            faturamentoUltimos12Meses.add(new PontoMensalDto(mes, valor));
        }

        List<ItemContagemDto> servicosMaisVendidos = comandaRepository.somarServicosVendidosEPeriodo(
                StatusComanda.FECHADA, inicioMes, fimMes, PageRequest.of(0, TOP_SERVICOS_MAIS_VENDIDOS));
        List<ItemContagemDto> atendimentosPorProfissional = comandaRepository
                .contarAtendimentosPorProfissionalEPeriodo(StatusComanda.FECHADA, inicioMes, fimMes);
        List<TotalPorFormaPagamentoDto> distribuicaoFormaPagamento = comandaRepository
                .somarPorFormaPagamentoEPeriodo(StatusComanda.FECHADA, inicioMes, fimMes);

        return new DashboardGraficosDto(faturamentoUltimos12Meses, servicosMaisVendidos, atendimentosPorProfissional,
                distribuicaoFormaPagamento);
    }

    private Barbearia obterBarbearia() {
        return barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
    }
}
