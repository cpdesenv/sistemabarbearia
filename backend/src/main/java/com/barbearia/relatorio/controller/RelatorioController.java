package com.barbearia.relatorio.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.barbearia.financeiro.domain.FormaPagamento;
import com.barbearia.relatorio.dto.ComparativoFaturamentoDto;
import com.barbearia.relatorio.dto.RelatorioAgendaDto;
import com.barbearia.relatorio.dto.RelatorioClientesDto;
import com.barbearia.relatorio.dto.RelatorioFaturamentoDto;
import com.barbearia.relatorio.dto.RelatorioHeatmapDto;
import com.barbearia.relatorio.dto.RelatorioProdutoDto;
import com.barbearia.relatorio.dto.ReprocessarRelatorioRequest;
import com.barbearia.relatorio.service.RelatorioAgendaService;
import com.barbearia.relatorio.service.RelatorioAgregacaoService;
import com.barbearia.relatorio.service.RelatorioClienteService;
import com.barbearia.relatorio.service.RelatorioFaturamentoService;
import com.barbearia.relatorio.service.RelatorioHeatmapService;
import com.barbearia.relatorio.service.RelatorioProdutoService;

@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
@Tag(name = "Relatórios")
public class RelatorioController {

    private final RelatorioFaturamentoService relatorioFaturamentoService;
    private final RelatorioAgendaService relatorioAgendaService;
    private final RelatorioClienteService relatorioClienteService;
    private final RelatorioProdutoService relatorioProdutoService;
    private final RelatorioHeatmapService relatorioHeatmapService;
    private final RelatorioAgregacaoService relatorioAgregacaoService;

    @GetMapping("/faturamento")
    public RelatorioFaturamentoDto faturamento(
            @RequestParam LocalDate dataInicial,
            @RequestParam LocalDate dataFinal,
            @RequestParam(required = false) UUID profissionalUuid,
            @RequestParam(required = false) UUID servicoUuid,
            @RequestParam(required = false) FormaPagamento formaPagamento) {
        return relatorioFaturamentoService.consultar(dataInicial, dataFinal, profissionalUuid, servicoUuid,
                formaPagamento);
    }

    @GetMapping("/faturamento/comparativo")
    public ComparativoFaturamentoDto comparativoFaturamento(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @RequestParam(required = false) UUID profissionalUuid,
            @RequestParam(required = false) UUID servicoUuid,
            @RequestParam(required = false) FormaPagamento formaPagamento) {
        return relatorioFaturamentoService.comparativo(mes, profissionalUuid, servicoUuid, formaPagamento);
    }

    @GetMapping("/agenda")
    public RelatorioAgendaDto agenda(
            @RequestParam LocalDate dataInicial,
            @RequestParam LocalDate dataFinal,
            @RequestParam(required = false) UUID profissionalUuid) {
        return relatorioAgendaService.consultar(dataInicial, dataFinal, profissionalUuid);
    }

    @GetMapping("/clientes")
    public RelatorioClientesDto clientes(
            @RequestParam LocalDate dataInicial,
            @RequestParam LocalDate dataFinal) {
        return relatorioClienteService.consultar(dataInicial, dataFinal);
    }

    @GetMapping("/produtos")
    public RelatorioProdutoDto produtos(
            @RequestParam LocalDate dataInicial,
            @RequestParam LocalDate dataFinal) {
        return relatorioProdutoService.consultar(dataInicial, dataFinal);
    }

    @GetMapping("/heatmap-horarios")
    public RelatorioHeatmapDto heatmapHorarios(
            @RequestParam LocalDate dataInicial,
            @RequestParam LocalDate dataFinal) {
        return relatorioHeatmapService.consultar(dataInicial, dataFinal);
    }

    @PostMapping("/reprocessar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public void reprocessar(@Valid @RequestBody ReprocessarRelatorioRequest requisicao) {
        relatorioAgregacaoService.reprocessar(requisicao.dataInicial(), requisicao.dataFinal());
    }
}
