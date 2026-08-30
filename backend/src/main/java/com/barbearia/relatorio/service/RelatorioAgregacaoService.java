package com.barbearia.relatorio.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.agenda.domain.StatusAgendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.financeiro.domain.StatusComanda;
import com.barbearia.financeiro.repository.ComandaRepository;
import com.barbearia.horario.domain.JanelaHorario;
import com.barbearia.horario.repository.JanelaHorarioRepository;
import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.repository.ProfissionalRepository;
import com.barbearia.relatorio.domain.RelatorioAgendaDiario;
import com.barbearia.relatorio.domain.RelatorioClienteDiario;
import com.barbearia.relatorio.domain.RelatorioServicoDiario;
import com.barbearia.relatorio.dto.AgregacaoAgendaDto;
import com.barbearia.relatorio.dto.AgregacaoClienteDto;
import com.barbearia.relatorio.repository.RelatorioAgendaDiarioRepository;
import com.barbearia.relatorio.repository.RelatorioClienteDiarioRepository;
import com.barbearia.relatorio.repository.RelatorioServicoDiarioRepository;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Poe as tabelas de agregacao diaria (Fase 11) em dia. {@link #agregarDia}
 * e' idempotente — apaga e recalcula as linhas do dia informado — para poder
 * ser chamado tanto pelo job noturno ({@code RelatorioAgregacaoScheduler},
 * so' o dia anterior) quanto pelo endpoint de reprocessamento (backfill do
 * historico apos o deploy desta fase, ou correcao apos um estorno tardio
 * num dia ja agregado).
 *
 * <p>{@link #calcularAgendaDoDia} e {@link #calcularClientesDoDia} sao
 * publicos e reaproveitados pelos servicos de leitura (ex.:
 * {@code RelatorioAgendaService}) para computar o dia corrente ao vivo, sem
 * persistir — o job so' agrega "ontem" (ver
 * {@code RelatorioAgregacaoScheduler}), entao hoje nunca esta nas tabelas.
 */
@Service
@RequiredArgsConstructor
public class RelatorioAgregacaoService {

    private final BarbeariaRepository barbeariaRepository;
    private final ComandaRepository comandaRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final JanelaHorarioRepository janelaHorarioRepository;
    private final RelatorioServicoDiarioRepository relatorioServicoDiarioRepository;
    private final RelatorioAgendaDiarioRepository relatorioAgendaDiarioRepository;
    private final RelatorioClienteDiarioRepository relatorioClienteDiarioRepository;

    @Transactional
    public void agregarDia(LocalDate data) {
        Instant[] periodo = periodoDoDia(data);
        Instant inicio = periodo[0];
        Instant fim = periodo[1];

        relatorioServicoDiarioRepository.deleteByData(data);
        comandaRepository.agregarServicosPorPeriodo(StatusComanda.FECHADA, inicio, fim).stream()
                .map(agregacao -> new RelatorioServicoDiario(data, agregacao))
                .forEach(relatorioServicoDiarioRepository::save);

        relatorioAgendaDiarioRepository.deleteByData(data);
        calcularAgendaDoDia(data).stream()
                .map(agregacao -> new RelatorioAgendaDiario(data, agregacao))
                .forEach(relatorioAgendaDiarioRepository::save);

        relatorioClienteDiarioRepository.deleteByData(data);
        AgregacaoClienteDto clientes = calcularClientesDoDia(data);
        relatorioClienteDiarioRepository.save(new RelatorioClienteDiario(data, clientes.clientesNovos(),
                clientes.clientesRecorrentes(), clientes.atendimentosTotais()));
    }

    @Transactional
    public void reprocessar(LocalDate dataInicial, LocalDate dataFinal) {
        if (dataFinal.isBefore(dataInicial)) {
            throw new NegocioException("A data final nao pode ser anterior a data inicial.");
        }
        for (LocalDate data = dataInicial; !data.isAfter(dataFinal); data = data.plusDays(1)) {
            agregarDia(data);
        }
    }

    /** Uma linha por profissional ATIVO, mesmo sem nenhum agendamento naquele dia — ver javadoc de {@code RelatorioAgendaDiario}. */
    @Transactional(readOnly = true)
    public List<AgregacaoAgendaDto> calcularAgendaDoDia(LocalDate data) {
        Instant[] periodo = periodoDoDia(data);

        Map<Long, int[]> contagemPorProfissional = new HashMap<>();
        Map<Long, Integer> minutosOcupadosPorProfissional = new HashMap<>();
        for (Agendamento agendamento : agendamentoRepository.buscarComProfissionalNoPeriodo(periodo[0], periodo[1])) {
            Long profissionalId = agendamento.getProfissional().getId();
            int[] contagem = contagemPorProfissional.computeIfAbsent(profissionalId, id -> new int[3]);
            if (agendamento.getStatus() == StatusAgendamento.FINALIZADO) {
                contagem[0]++;
            } else if (agendamento.getStatus() == StatusAgendamento.CANCELADO) {
                contagem[1]++;
            } else if (agendamento.getStatus() == StatusAgendamento.NAO_COMPARECEU) {
                contagem[2]++;
            }
            if (agendamento.getStatus() != StatusAgendamento.CANCELADO) {
                int minutos = (int) Duration.between(agendamento.getInicio(), agendamento.getFim()).toMinutes();
                minutosOcupadosPorProfissional.merge(profissionalId, minutos, Integer::sum);
            }
        }

        int diaSemana = data.getDayOfWeek().getValue();
        List<AgregacaoAgendaDto> resultado = new ArrayList<>();
        for (Profissional profissional : profissionalRepository.findByAtivoTrue()) {
            int minutosCapacidade = janelaHorarioRepository
                    .findByProfissionalAndDiaSemanaOrderByHoraInicioAsc(profissional, diaSemana).stream()
                    .mapToInt(this::minutosDaJanela)
                    .sum();
            int[] contagem = contagemPorProfissional.getOrDefault(profissional.getId(), new int[3]);
            int minutosOcupados = minutosOcupadosPorProfissional.getOrDefault(profissional.getId(), 0);

            resultado.add(new AgregacaoAgendaDto(profissional.getId(), profissional.getNome(), contagem[0],
                    contagem[1], contagem[2], minutosCapacidade, minutosOcupados));
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public AgregacaoClienteDto calcularClientesDoDia(LocalDate data) {
        Instant[] periodo = periodoDoDia(data);

        List<Long> clienteIds = comandaRepository.buscarClienteIdsAtendidosPorPeriodo(StatusComanda.FECHADA,
                periodo[0], periodo[1]);
        int novos = 0;
        int recorrentes = 0;
        for (Long clienteId : clienteIds) {
            boolean recorrente = comandaRepository.existeAtendimentoAnteriorDoCliente(StatusComanda.FECHADA,
                    clienteId, periodo[0]);
            if (recorrente) {
                recorrentes++;
            } else {
                novos++;
            }
        }

        int atendimentosTotais = (int) comandaRepository.contarPorStatusEPeriodo(StatusComanda.FECHADA, periodo[0],
                periodo[1]);
        return new AgregacaoClienteDto(novos, recorrentes, atendimentosTotais);
    }

    private Instant[] periodoDoDia(LocalDate data) {
        ZoneId fuso = obterFusoHorario();
        return new Instant[] { data.atStartOfDay(fuso).toInstant(), data.plusDays(1).atStartOfDay(fuso).toInstant() };
    }

    private int minutosDaJanela(JanelaHorario janela) {
        return (int) Duration.between(janela.getHoraInicio(), janela.getHoraFim()).toMinutes();
    }

    private ZoneId obterFusoHorario() {
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
        return ZoneId.of(barbearia.getFusoHorario());
    }
}
