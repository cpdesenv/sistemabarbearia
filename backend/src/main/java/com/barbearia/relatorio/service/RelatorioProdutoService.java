package com.barbearia.relatorio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.relatorio.domain.RelatorioProdutoDiario;
import com.barbearia.relatorio.dto.AgregacaoProdutoDto;
import com.barbearia.relatorio.dto.LinhaProdutoDto;
import com.barbearia.relatorio.dto.RelatorioProdutoDto;
import com.barbearia.relatorio.repository.RelatorioProdutoDiarioRepository;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Le' produtos mais vendidos e margem a partir de
 * {@code relatorio_produto_diario} mais o dia corrente ao vivo (ainda nao
 * agregado — mesmo padrao de {@code RelatorioFaturamentoService}).
 */
@Service
@RequiredArgsConstructor
public class RelatorioProdutoService {

    private final BarbeariaRepository barbeariaRepository;
    private final RelatorioProdutoDiarioRepository relatorioProdutoDiarioRepository;
    private final RelatorioAgregacaoService relatorioAgregacaoService;

    @Transactional(readOnly = true)
    public RelatorioProdutoDto consultar(LocalDate dataInicial, LocalDate dataFinal) {
        List<AgregacaoProdutoDto> linhas = buscarLinhas(dataInicial, dataFinal);

        Map<String, LinhaProdutoDto> acumulado = new LinkedHashMap<>();
        for (AgregacaoProdutoDto linha : linhas) {
            acumulado.merge(linha.produtoNome(), paraLinha(linha.produtoNome(), linha.quantidadeVendida(),
                    linha.valorTotal(), linha.custoTotal()), this::somar);
        }

        List<LinhaProdutoDto> porProduto = acumulado.values().stream()
                .sorted(Comparator.comparing(LinhaProdutoDto::quantidadeVendida).reversed())
                .toList();

        BigDecimal valorTotal = porProduto.stream().map(LinhaProdutoDto::valorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal custoTotal = porProduto.stream().map(LinhaProdutoDto::custoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal margemTotal = valorTotal.subtract(custoTotal);

        return new RelatorioProdutoDto(dataInicial, dataFinal, valorTotal, custoTotal, margemTotal,
                margemPercentual(margemTotal, valorTotal), porProduto);
    }

    private LinhaProdutoDto paraLinha(String nome, long quantidadeVendida, BigDecimal valorTotal,
            BigDecimal custoTotal) {
        BigDecimal margemTotal = valorTotal.subtract(custoTotal);
        return new LinhaProdutoDto(nome, quantidadeVendida, valorTotal, custoTotal, margemTotal,
                margemPercentual(margemTotal, valorTotal));
    }

    private LinhaProdutoDto somar(LinhaProdutoDto a, LinhaProdutoDto b) {
        return paraLinha(a.nome(), a.quantidadeVendida() + b.quantidadeVendida(), a.valorTotal().add(b.valorTotal()),
                a.custoTotal().add(b.custoTotal()));
    }

    private BigDecimal margemPercentual(BigDecimal margemTotal, BigDecimal valorTotal) {
        if (valorTotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return margemTotal.multiply(BigDecimal.valueOf(100)).divide(valorTotal, 2, RoundingMode.HALF_UP);
    }

    /**
     * Combina as linhas ja agregadas (historico, {@code relatorio_produto_diario})
     * com uma consulta ao vivo do dia corrente, se ele cair dentro do periodo
     * pedido — o job noturno so' processa "ontem" (ver
     * {@code RelatorioAgregacaoScheduler}), entao hoje nunca esta na tabela.
     */
    private List<AgregacaoProdutoDto> buscarLinhas(LocalDate dataInicial, LocalDate dataFinal) {
        ZoneId fuso = obterFusoHorario();
        LocalDate hoje = LocalDate.now(fuso);

        List<AgregacaoProdutoDto> linhas = new ArrayList<>();

        LocalDate fimHistorico = dataFinal.isBefore(hoje) ? dataFinal : hoje.minusDays(1);
        if (!dataInicial.isAfter(fimHistorico)) {
            relatorioProdutoDiarioRepository.findByDataBetween(dataInicial, fimHistorico).stream()
                    .map(this::paraAgregacaoDto)
                    .forEach(linhas::add);
        }

        if (!dataInicial.isAfter(hoje) && !dataFinal.isBefore(hoje)) {
            linhas.addAll(relatorioAgregacaoService.calcularProdutosDoDia(hoje));
        }

        return linhas;
    }

    private AgregacaoProdutoDto paraAgregacaoDto(RelatorioProdutoDiario linha) {
        return new AgregacaoProdutoDto(linha.getProdutoId(), linha.getProdutoNome(), linha.getQuantidadeVendida(),
                linha.getValorTotal(), linha.getCustoTotal());
    }

    private ZoneId obterFusoHorario() {
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
        return ZoneId.of(barbearia.getFusoHorario());
    }
}
