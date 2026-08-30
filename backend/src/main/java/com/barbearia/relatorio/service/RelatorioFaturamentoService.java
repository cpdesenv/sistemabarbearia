package com.barbearia.relatorio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.financeiro.domain.FormaPagamento;
import com.barbearia.financeiro.domain.StatusComanda;
import com.barbearia.financeiro.dto.TotalPorFormaPagamentoDto;
import com.barbearia.financeiro.repository.ComandaRepository;
import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.repository.ProfissionalRepository;
import com.barbearia.relatorio.domain.RelatorioServicoDiario;
import com.barbearia.relatorio.dto.AgregacaoServicoDto;
import com.barbearia.relatorio.dto.ComparativoFaturamentoDto;
import com.barbearia.relatorio.dto.LinhaFaturamentoDto;
import com.barbearia.relatorio.dto.RelatorioFaturamentoDto;
import com.barbearia.relatorio.repository.RelatorioServicoDiarioRepository;
import com.barbearia.relatorio.repository.RelatorioServicoDiarioSpecifications;
import com.barbearia.servico.domain.Servico;
import com.barbearia.servico.repository.ServicoRepository;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Le' o relatorio de faturamento a partir de {@code relatorio_servico_diario}
 * (rapido mesmo em volume alto — ver {@code RelatorioAgregacaoService}) mais
 * o dia corrente, que ainda nao foi agregado pelo job noturno e por isso
 * entra por uma consulta ao vivo pontual (poucas linhas, sem custo).
 */
@Service
@RequiredArgsConstructor
public class RelatorioFaturamentoService {

    private final BarbeariaRepository barbeariaRepository;
    private final ComandaRepository comandaRepository;
    private final RelatorioServicoDiarioRepository relatorioServicoDiarioRepository;
    private final ProfissionalRepository profissionalRepository;
    private final ServicoRepository servicoRepository;

    @Transactional(readOnly = true)
    public RelatorioFaturamentoDto consultar(LocalDate dataInicial, LocalDate dataFinal, UUID profissionalUuid,
            UUID servicoUuid, FormaPagamento formaPagamento) {
        Long profissionalId = resolverProfissionalId(profissionalUuid);
        Long servicoId = resolverServicoId(servicoUuid);

        List<AgregacaoServicoDto> linhas = buscarLinhas(dataInicial, dataFinal, profissionalId, servicoId,
                formaPagamento);

        BigDecimal valorTotal = linhas.stream().map(AgregacaoServicoDto::valorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal comissaoTotal = linhas.stream().map(AgregacaoServicoDto::comissaoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long quantidadeAtendimentos = linhas.stream().mapToLong(AgregacaoServicoDto::quantidade).sum();

        List<LinhaFaturamentoDto> porServico = agruparPor(linhas, AgregacaoServicoDto::servicoNome);
        List<LinhaFaturamentoDto> porProfissional = agruparPor(linhas, AgregacaoServicoDto::profissionalNome);
        List<TotalPorFormaPagamentoDto> porFormaPagamento = agruparPorFormaPagamento(linhas);

        return new RelatorioFaturamentoDto(dataInicial, dataFinal, valorTotal, comissaoTotal, quantidadeAtendimentos,
                porServico, porProfissional, porFormaPagamento);
    }

    @Transactional(readOnly = true)
    public ComparativoFaturamentoDto comparativo(YearMonth mes, UUID profissionalUuid, UUID servicoUuid,
            FormaPagamento formaPagamento) {
        Long profissionalId = resolverProfissionalId(profissionalUuid);
        Long servicoId = resolverServicoId(servicoUuid);

        BigDecimal valorMesAtual = somarValorDoMes(mes, profissionalId, servicoId, formaPagamento);
        BigDecimal valorMesAnterior = somarValorDoMes(mes.minusMonths(1), profissionalId, servicoId, formaPagamento);
        BigDecimal valorMesmoMesAnoAnterior = somarValorDoMes(mes.minusYears(1), profissionalId, servicoId,
                formaPagamento);

        return new ComparativoFaturamentoDto(mes, valorMesAtual, valorMesAnterior,
                variacaoPercentual(valorMesAtual, valorMesAnterior), valorMesmoMesAnoAnterior,
                variacaoPercentual(valorMesAtual, valorMesmoMesAnoAnterior));
    }

    private BigDecimal somarValorDoMes(YearMonth mes, Long profissionalId, Long servicoId,
            FormaPagamento formaPagamento) {
        List<AgregacaoServicoDto> linhas = buscarLinhas(mes.atDay(1), mes.atEndOfMonth(), profissionalId, servicoId,
                formaPagamento);
        return linhas.stream().map(AgregacaoServicoDto::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal variacaoPercentual(BigDecimal valorAtual, BigDecimal valorBase) {
        if (valorBase.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return valorAtual.subtract(valorBase).multiply(BigDecimal.valueOf(100))
                .divide(valorBase, 2, RoundingMode.HALF_UP);
    }

    /**
     * Combina as linhas ja agregadas (historico, {@code relatorio_servico_diario})
     * com uma consulta ao vivo do dia corrente, se ele cair dentro do periodo
     * pedido — o job noturno so' processa "ontem" (ver
     * {@code RelatorioAgregacaoScheduler}), entao hoje nunca esta na tabela.
     */
    private List<AgregacaoServicoDto> buscarLinhas(LocalDate dataInicial, LocalDate dataFinal, Long profissionalId,
            Long servicoId, FormaPagamento formaPagamento) {
        ZoneId fuso = obterFusoHorario();
        LocalDate hoje = LocalDate.now(fuso);

        List<AgregacaoServicoDto> linhas = new ArrayList<>();

        LocalDate fimHistorico = dataFinal.isBefore(hoje) ? dataFinal : hoje.minusDays(1);
        if (!dataInicial.isAfter(fimHistorico)) {
            var spec = RelatorioServicoDiarioSpecifications.comFiltros(dataInicial, fimHistorico, profissionalId,
                    servicoId, formaPagamento);
            relatorioServicoDiarioRepository.findAll(spec).stream()
                    .map(this::paraAgregacaoDto)
                    .forEach(linhas::add);
        }

        if (!dataInicial.isAfter(hoje) && !dataFinal.isBefore(hoje)) {
            var inicioHoje = hoje.atStartOfDay(fuso).toInstant();
            var fimHoje = hoje.plusDays(1).atStartOfDay(fuso).toInstant();
            comandaRepository.agregarServicosPorPeriodo(StatusComanda.FECHADA, inicioHoje, fimHoje).stream()
                    .filter(item -> profissionalId == null || profissionalId.equals(item.profissionalId()))
                    .filter(item -> servicoId == null || servicoId.equals(item.servicoId()))
                    .filter(item -> formaPagamento == null || formaPagamento == item.formaPagamento())
                    .forEach(linhas::add);
        }

        return linhas;
    }

    private AgregacaoServicoDto paraAgregacaoDto(RelatorioServicoDiario linha) {
        return new AgregacaoServicoDto(linha.getProfissionalId(), linha.getProfissionalNome(), linha.getServicoId(),
                linha.getServicoNome(), linha.getFormaPagamento(), linha.getQuantidade(), linha.getValorTotal(),
                linha.getComissaoTotal());
    }

    private List<LinhaFaturamentoDto> agruparPor(List<AgregacaoServicoDto> linhas,
            Function<AgregacaoServicoDto, String> chave) {
        Map<String, LinhaFaturamentoDto> acumulado = new LinkedHashMap<>();
        for (AgregacaoServicoDto linha : linhas) {
            acumulado.merge(chave.apply(linha),
                    new LinhaFaturamentoDto(chave.apply(linha), linha.quantidade(), linha.valorTotal(),
                            linha.comissaoTotal()),
                    (a, b) -> new LinhaFaturamentoDto(a.nome(), a.quantidade() + b.quantidade(),
                            a.valorTotal().add(b.valorTotal()), a.comissaoTotal().add(b.comissaoTotal())));
        }
        return acumulado.values().stream()
                .sorted(Comparator.comparing(LinhaFaturamentoDto::valorTotal).reversed())
                .toList();
    }

    private List<TotalPorFormaPagamentoDto> agruparPorFormaPagamento(List<AgregacaoServicoDto> linhas) {
        Map<FormaPagamento, BigDecimal> acumulado = new EnumMap<>(FormaPagamento.class);
        for (AgregacaoServicoDto linha : linhas) {
            acumulado.merge(linha.formaPagamento(), linha.valorTotal(), BigDecimal::add);
        }
        return acumulado.entrySet().stream()
                .map(entrada -> new TotalPorFormaPagamentoDto(entrada.getKey(), entrada.getValue()))
                .toList();
    }

    private Long resolverProfissionalId(UUID profissionalUuid) {
        if (profissionalUuid == null) {
            return null;
        }
        Profissional profissional = profissionalRepository.findByUuidPublico(profissionalUuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Profissional nao encontrado."));
        return profissional.getId();
    }

    private Long resolverServicoId(UUID servicoUuid) {
        if (servicoUuid == null) {
            return null;
        }
        Servico servico = servicoRepository.findByUuidPublico(servicoUuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Servico nao encontrado."));
        return servico.getId();
    }

    private ZoneId obterFusoHorario() {
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
        return ZoneId.of(barbearia.getFusoHorario());
    }
}
