package com.barbearia.relatorio.service;

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
import com.barbearia.relatorio.domain.RelatorioHorarioDiario;
import com.barbearia.relatorio.dto.AgregacaoHorarioDto;
import com.barbearia.relatorio.dto.CelulaHeatmapDto;
import com.barbearia.relatorio.dto.RelatorioHeatmapDto;
import com.barbearia.relatorio.repository.RelatorioHorarioDiarioRepository;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Le' o heatmap de horarios de maior movimento a partir de
 * {@code relatorio_horario_diario} mais o dia corrente ao vivo (ainda nao
 * agregado — mesmo padrao de {@code RelatorioFaturamentoService}). Dia da
 * semana e' calculado em Java a partir de {@code data} (nao armazenado, ver
 * javadoc de {@code RelatorioHorarioDiario}).
 */
@Service
@RequiredArgsConstructor
public class RelatorioHeatmapService {

    private record ChaveCelula(int diaSemana, int hora) {
    }

    private final BarbeariaRepository barbeariaRepository;
    private final RelatorioHorarioDiarioRepository relatorioHorarioDiarioRepository;
    private final RelatorioAgregacaoService relatorioAgregacaoService;

    @Transactional(readOnly = true)
    public RelatorioHeatmapDto consultar(LocalDate dataInicial, LocalDate dataFinal) {
        ZoneId fuso = obterFusoHorario();
        LocalDate hoje = LocalDate.now(fuso);

        Map<ChaveCelula, Long> acumulado = new LinkedHashMap<>();

        LocalDate fimHistorico = dataFinal.isBefore(hoje) ? dataFinal : hoje.minusDays(1);
        if (!dataInicial.isAfter(fimHistorico)) {
            for (RelatorioHorarioDiario linha : relatorioHorarioDiarioRepository.findByDataBetween(dataInicial,
                    fimHistorico)) {
                ChaveCelula chave = new ChaveCelula(linha.getData().getDayOfWeek().getValue(), linha.getHora());
                acumulado.merge(chave, (long) linha.getQuantidadeFinalizados(), Long::sum);
            }
        }

        if (!dataInicial.isAfter(hoje) && !dataFinal.isBefore(hoje)) {
            int diaSemanaHoje = hoje.getDayOfWeek().getValue();
            for (AgregacaoHorarioDto agregacao : relatorioAgregacaoService.calcularHorariosDoDia(hoje)) {
                ChaveCelula chave = new ChaveCelula(diaSemanaHoje, agregacao.hora());
                acumulado.merge(chave, agregacao.quantidadeFinalizados(), Long::sum);
            }
        }

        List<CelulaHeatmapDto> celulas = new ArrayList<>();
        acumulado.forEach((chave, quantidade) -> celulas.add(new CelulaHeatmapDto(chave.diaSemana(), chave.hora(),
                quantidade)));
        celulas.sort(Comparator.comparingInt(CelulaHeatmapDto::diaSemana).thenComparingInt(CelulaHeatmapDto::hora));

        return new RelatorioHeatmapDto(dataInicial, dataFinal, celulas);
    }

    private ZoneId obterFusoHorario() {
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
        return ZoneId.of(barbearia.getFusoHorario());
    }
}
