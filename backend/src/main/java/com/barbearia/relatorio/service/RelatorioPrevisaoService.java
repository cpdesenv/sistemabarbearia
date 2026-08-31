package com.barbearia.relatorio.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.financeiro.domain.StatusContaPagar;
import com.barbearia.financeiro.dto.ContaPagarDto;
import com.barbearia.financeiro.service.ContaPagarService;
import com.barbearia.produto.service.ProdutoService;
import com.barbearia.relatorio.dto.RelatorioFaturamentoDto;
import com.barbearia.relatorio.dto.RelatorioPrevisaoDto;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Compoe a previsao de compromissos (Fase 11) a partir de tres fontes ja
 * existentes — nenhuma delas e' nova nem precisa de tabela de agregacao,
 * porque isto e' um snapshot do momento atual: comissao apurada no mes
 * corrente ({@code RelatorioFaturamentoService}), produtos abaixo do estoque
 * minimo ({@code ProdutoService}, Fase 5) e contas a pagar vencidas
 * ({@code ContaPagarService}, Fase 5).
 */
@Service
@RequiredArgsConstructor
public class RelatorioPrevisaoService {

    private final BarbeariaRepository barbeariaRepository;
    private final RelatorioFaturamentoService relatorioFaturamentoService;
    private final ProdutoService produtoService;
    private final ContaPagarService contaPagarService;

    @Transactional(readOnly = true)
    public RelatorioPrevisaoDto consultar() {
        LocalDate hoje = LocalDate.now(obterFusoHorario());
        LocalDate inicioMes = hoje.withDayOfMonth(1);

        RelatorioFaturamentoDto faturamentoMes = relatorioFaturamentoService.consultar(inicioMes, hoje, null, null,
                null);

        var contasVencidas = contaPagarService.listar(StatusContaPagar.PENDENTE).stream()
                .filter(ContaPagarDto::vencida)
                .toList();

        return new RelatorioPrevisaoDto(faturamentoMes.comissaoTotal(), faturamentoMes.porProfissional(),
                produtoService.alertaEstoqueMinimo(), contasVencidas);
    }

    private ZoneId obterFusoHorario() {
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
        return ZoneId.of(barbearia.getFusoHorario());
    }
}
