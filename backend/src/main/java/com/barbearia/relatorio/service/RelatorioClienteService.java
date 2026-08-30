package com.barbearia.relatorio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.relatorio.domain.RelatorioClienteDiario;
import com.barbearia.relatorio.dto.RelatorioClientesDto;
import com.barbearia.relatorio.repository.RelatorioClienteDiarioRepository;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Le' clientes novos vs. recorrentes a partir de
 * {@code relatorio_cliente_diario} mais o dia corrente ao vivo — mesmo
 * padrao de {@code RelatorioFaturamentoService}/{@code RelatorioAgendaService}.
 */
@Service
@RequiredArgsConstructor
public class RelatorioClienteService {

    private final BarbeariaRepository barbeariaRepository;
    private final RelatorioClienteDiarioRepository relatorioClienteDiarioRepository;
    private final RelatorioAgregacaoService relatorioAgregacaoService;

    @Transactional(readOnly = true)
    public RelatorioClientesDto consultar(LocalDate dataInicial, LocalDate dataFinal) {
        ZoneId fuso = obterFusoHorario();
        LocalDate hoje = LocalDate.now(fuso);

        int novos = 0;
        int recorrentes = 0;
        int atendimentosTotais = 0;

        LocalDate fimHistorico = dataFinal.isBefore(hoje) ? dataFinal : hoje.minusDays(1);
        if (!dataInicial.isAfter(fimHistorico)) {
            for (RelatorioClienteDiario linha : relatorioClienteDiarioRepository.findByDataBetween(dataInicial,
                    fimHistorico)) {
                novos += linha.getClientesNovos();
                recorrentes += linha.getClientesRecorrentes();
                atendimentosTotais += linha.getAtendimentosTotais();
            }
        }

        if (!dataInicial.isAfter(hoje) && !dataFinal.isBefore(hoje)) {
            var hojeAgregado = relatorioAgregacaoService.calcularClientesDoDia(hoje);
            novos += hojeAgregado.clientesNovos();
            recorrentes += hojeAgregado.clientesRecorrentes();
            atendimentosTotais += hojeAgregado.atendimentosTotais();
        }

        BigDecimal taxaDeRetorno = BigDecimal.ZERO;
        int totalClientes = novos + recorrentes;
        if (totalClientes > 0) {
            taxaDeRetorno = BigDecimal.valueOf(recorrentes).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalClientes), 2, RoundingMode.HALF_UP);
        }

        return new RelatorioClientesDto(dataInicial, dataFinal, novos, recorrentes, atendimentosTotais,
                taxaDeRetorno);
    }

    private ZoneId obterFusoHorario() {
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
        return ZoneId.of(barbearia.getFusoHorario());
    }
}
