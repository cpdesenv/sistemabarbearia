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
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.repository.ProfissionalRepository;
import com.barbearia.relatorio.domain.RelatorioAgendaDiario;
import com.barbearia.relatorio.dto.AgregacaoAgendaDto;
import com.barbearia.relatorio.dto.LinhaAgendaDto;
import com.barbearia.relatorio.dto.RelatorioAgendaDto;
import com.barbearia.relatorio.repository.RelatorioAgendaDiarioRepository;
import com.barbearia.relatorio.repository.RelatorioAgendaDiarioSpecifications;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Le' cancelamentos, faltas e ocupacao a partir de
 * {@code relatorio_agenda_diario} mais o dia corrente ao vivo (ainda nao
 * agregado — mesmo padrao de {@code RelatorioFaturamentoService}).
 */
@Service
@RequiredArgsConstructor
public class RelatorioAgendaService {

    private final BarbeariaRepository barbeariaRepository;
    private final RelatorioAgendaDiarioRepository relatorioAgendaDiarioRepository;
    private final RelatorioAgregacaoService relatorioAgregacaoService;
    private final ProfissionalRepository profissionalRepository;

    @Transactional(readOnly = true)
    public RelatorioAgendaDto consultar(LocalDate dataInicial, LocalDate dataFinal, UUID profissionalUuid) {
        Long profissionalId = resolverProfissionalId(profissionalUuid);
        List<AgregacaoAgendaDto> linhas = buscarLinhas(dataInicial, dataFinal, profissionalId);

        int totalFinalizados = linhas.stream().mapToInt(AgregacaoAgendaDto::quantidadeFinalizados).sum();
        int totalCancelados = linhas.stream().mapToInt(AgregacaoAgendaDto::quantidadeCancelados).sum();
        int totalNaoCompareceu = linhas.stream().mapToInt(AgregacaoAgendaDto::quantidadeNaoCompareceu).sum();
        int totalCapacidade = linhas.stream().mapToInt(AgregacaoAgendaDto::minutosCapacidade).sum();
        int totalOcupados = linhas.stream().mapToInt(AgregacaoAgendaDto::minutosOcupados).sum();

        List<LinhaAgendaDto> porProfissional = agruparPorProfissional(linhas);

        return new RelatorioAgendaDto(dataInicial, dataFinal, totalFinalizados, totalCancelados, totalNaoCompareceu,
                calcularTaxaOcupacao(totalOcupados, totalCapacidade), porProfissional);
    }

    private List<LinhaAgendaDto> agruparPorProfissional(List<AgregacaoAgendaDto> linhas) {
        Map<String, int[]> acumulado = new LinkedHashMap<>();
        for (AgregacaoAgendaDto linha : linhas) {
            int[] soma = acumulado.computeIfAbsent(linha.profissionalNome(), nome -> new int[5]);
            soma[0] += linha.quantidadeFinalizados();
            soma[1] += linha.quantidadeCancelados();
            soma[2] += linha.quantidadeNaoCompareceu();
            soma[3] += linha.minutosCapacidade();
            soma[4] += linha.minutosOcupados();
        }
        return acumulado.entrySet().stream()
                .map(entrada -> new LinhaAgendaDto(entrada.getKey(), entrada.getValue()[0], entrada.getValue()[1],
                        entrada.getValue()[2], calcularTaxaOcupacao(entrada.getValue()[4], entrada.getValue()[3])))
                .sorted(Comparator.comparing(LinhaAgendaDto::profissionalNome))
                .toList();
    }

    private BigDecimal calcularTaxaOcupacao(int minutosOcupados, int minutosCapacidade) {
        if (minutosCapacidade == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(minutosOcupados).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(minutosCapacidade), 2, RoundingMode.HALF_UP);
    }

    private List<AgregacaoAgendaDto> buscarLinhas(LocalDate dataInicial, LocalDate dataFinal, Long profissionalId) {
        ZoneId fuso = obterFusoHorario();
        LocalDate hoje = LocalDate.now(fuso);

        List<AgregacaoAgendaDto> linhas = new ArrayList<>();

        LocalDate fimHistorico = dataFinal.isBefore(hoje) ? dataFinal : hoje.minusDays(1);
        if (!dataInicial.isAfter(fimHistorico)) {
            var spec = RelatorioAgendaDiarioSpecifications.comFiltros(dataInicial, fimHistorico, profissionalId);
            relatorioAgendaDiarioRepository.findAll(spec).stream()
                    .map(this::paraAgregacaoDto)
                    .forEach(linhas::add);
        }

        if (!dataInicial.isAfter(hoje) && !dataFinal.isBefore(hoje)) {
            relatorioAgregacaoService.calcularAgendaDoDia(hoje).stream()
                    .filter(item -> profissionalId == null || profissionalId.equals(item.profissionalId()))
                    .forEach(linhas::add);
        }

        return linhas;
    }

    private AgregacaoAgendaDto paraAgregacaoDto(RelatorioAgendaDiario linha) {
        return new AgregacaoAgendaDto(linha.getProfissionalId(), linha.getProfissionalNome(),
                linha.getQuantidadeFinalizados(), linha.getQuantidadeCancelados(),
                linha.getQuantidadeNaoCompareceu(), linha.getMinutosCapacidade(), linha.getMinutosOcupados());
    }

    private Long resolverProfissionalId(UUID profissionalUuid) {
        if (profissionalUuid == null) {
            return null;
        }
        Profissional profissional = profissionalRepository.findByUuidPublico(profissionalUuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Profissional nao encontrado."));
        return profissional.getId();
    }

    private ZoneId obterFusoHorario() {
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
        return ZoneId.of(barbearia.getFusoHorario());
    }
}
