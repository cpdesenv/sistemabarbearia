package com.barbearia.agenda.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.agenda.dto.SlotDisponivelDto;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.horario.domain.Bloqueio;
import com.barbearia.horario.domain.JanelaHorario;
import com.barbearia.horario.repository.BloqueioRepository;
import com.barbearia.horario.repository.JanelaHorarioRepository;
import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.repository.ProfissionalRepository;
import com.barbearia.profissional.repository.ProfissionalServicoRepository;
import com.barbearia.servico.domain.Servico;
import com.barbearia.servico.repository.ServicoRepository;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * O motor de disponibilidade: calcula os horarios realmente livres de um ou
 * mais profissionais, considerando grade semanal, bloqueios, agendamentos
 * existentes, duracao total dos servicos e as antecedencias configuradas na
 * barbearia. Tambem valida um horario especifico antes de criar/alterar um
 * agendamento — as mesmas regras, aplicadas a um unico candidato.
 *
 * <p>Esta classe e' a autoridade em regra de negocio de disponibilidade; a
 * constraint de exclusao do banco (ver V16__cria_tabela_agendamento.sql)
 * existe como reforco contra corrida de concorrencia, nao como substituta
 * desta validacao.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private static final int MINUTOS_POR_DIA = 24 * 60;

    private final BarbeariaRepository barbeariaRepository;
    private final ProfissionalRepository profissionalRepository;
    private final ProfissionalServicoRepository profissionalServicoRepository;
    private final ServicoRepository servicoRepository;
    private final JanelaHorarioRepository janelaHorarioRepository;
    private final BloqueioRepository bloqueioRepository;
    private final AgendamentoRepository agendamentoRepository;

    @Transactional(readOnly = true)
    public List<SlotDisponivelDto> consultarDisponibilidade(LocalDate data, List<UUID> servicoUuids,
            UUID profissionalUuidOpcional) {
        Barbearia barbearia = obterBarbearia();
        ZoneId fuso = ZoneId.of(barbearia.getFusoHorario());

        List<Servico> servicos = resolverServicosAtivos(servicoUuids);
        int duracaoTotal = servicos.stream().mapToInt(Servico::getDuracaoMinutos).sum();
        if (duracaoTotal >= MINUTOS_POR_DIA) {
            throw new NegocioException("A soma da duracao dos servicos selecionados excede um dia inteiro.");
        }

        validarData(data, barbearia, fuso);

        List<Profissional> profissionais = resolverProfissionaisCapazes(profissionalUuidOpcional, servicos);

        Instant inicioDoDia = data.atStartOfDay(fuso).toInstant();
        Instant fimDoDia = data.plusDays(1).atStartOfDay(fuso).toInstant();
        Instant agora = Instant.now();
        Instant antecedenciaMinima = agora.plusSeconds(barbearia.getAntecedenciaMinimaAgendamentoMinutos() * 60L);
        int diaSemana = data.getDayOfWeek().getValue();

        return profissionais.stream()
                .flatMap(profissional -> gerarSlotsDoProfissional(profissional, data, fuso, diaSemana, duracaoTotal,
                        barbearia.getGranularidadeSlotMinutos(), inicioDoDia, fimDoDia, antecedenciaMinima).stream())
                .toList();
    }

    /**
     * Valida um horario especifico antes de criar/alterar um agendamento.
     * Lanca {@link NegocioException} com uma mensagem legivel para a
     * primeira regra violada.
     */
    @Transactional(readOnly = true)
    public void validarSlotParaAgendamento(Profissional profissional, Instant inicio, Instant fim,
            Long agendamentoIdParaIgnorar) {
        Barbearia barbearia = obterBarbearia();
        ZoneId fuso = ZoneId.of(barbearia.getFusoHorario());
        Instant agora = Instant.now();

        if (inicio.isBefore(agora)) {
            throw new NegocioException("Nao e possivel agendar num horario no passado.");
        }

        Instant antecedenciaMinima = agora.plusSeconds(barbearia.getAntecedenciaMinimaAgendamentoMinutos() * 60L);
        if (inicio.isBefore(antecedenciaMinima)) {
            throw new NegocioException("O agendamento precisa respeitar a antecedencia minima de "
                    + barbearia.getAntecedenciaMinimaAgendamentoMinutos() + " minuto(s).");
        }

        Instant antecedenciaMaxima = agora.plusSeconds(barbearia.getAntecedenciaMaximaAgendamentoDias() * 86400L);
        if (inicio.isAfter(antecedenciaMaxima)) {
            throw new NegocioException("O agendamento nao pode ser feito com mais de "
                    + barbearia.getAntecedenciaMaximaAgendamentoDias() + " dia(s) de antecedencia.");
        }

        ZonedDateTime inicioZoned = inicio.atZone(fuso);
        ZonedDateTime fimZoned = fim.atZone(fuso);
        if (!inicioZoned.toLocalDate().equals(fimZoned.toLocalDate())) {
            throw new NegocioException("O agendamento nao pode atravessar a virada do dia.");
        }

        int diaSemana = inicioZoned.getDayOfWeek().getValue();
        if (!existeJanelaQueComporta(profissional, diaSemana, inicioZoned.toLocalTime(), fimZoned.toLocalTime())) {
            throw new NegocioException("O horario esta fora do funcionamento do profissional.");
        }

        if (!bloqueioRepository.buscarSobrepondo(profissional, inicio, fim).isEmpty()) {
            throw new NegocioException("O horario esta bloqueado na agenda do profissional.");
        }

        if (!agendamentoRepository.buscarConflitantes(profissional, inicio, fim, agendamentoIdParaIgnorar)
                .isEmpty()) {
            throw new NegocioException("O profissional ja tem um agendamento nesse horario.");
        }
    }

    private List<SlotDisponivelDto> gerarSlotsDoProfissional(Profissional profissional, LocalDate data, ZoneId fuso,
            int diaSemana, int duracaoTotal, int granularidadeMinutos, Instant inicioDoDia, Instant fimDoDia,
            Instant antecedenciaMinima) {
        List<JanelaHorario> janelas = janelaHorarioRepository
                .findByProfissionalAndDiaSemanaOrderByHoraInicioAsc(profissional, diaSemana);
        if (janelas.isEmpty()) {
            return List.of();
        }

        List<Bloqueio> bloqueios = bloqueioRepository.buscarSobrepondo(profissional, inicioDoDia, fimDoDia);
        var conflitantes = agendamentoRepository.buscarConflitantes(profissional, inicioDoDia, fimDoDia, null);

        return janelas.stream()
                .flatMap(janela -> candidatosDaJanela(janela, duracaoTotal, granularidadeMinutos).stream())
                .map(horaInicioLocal -> new Candidato(
                        ZonedDateTime.of(data, horaInicioLocal, fuso).toInstant(),
                        ZonedDateTime.of(data, horaInicioLocal.plusMinutes(duracaoTotal), fuso).toInstant()))
                .filter(candidato -> !candidato.inicio().isBefore(antecedenciaMinima))
                .filter(candidato -> bloqueios.stream()
                        .noneMatch(b -> seSobrepoem(b.getInicio(), b.getFim(), candidato.inicio(), candidato.fim())))
                .filter(candidato -> conflitantes.stream()
                        .noneMatch(a -> seSobrepoem(a.getInicio(), a.getFim(), candidato.inicio(), candidato.fim())))
                .map(candidato -> new SlotDisponivelDto(profissional.getUuidPublico(), profissional.getNome(),
                        profissional.getCorAgenda(), candidato.inicio(), candidato.fim()))
                .toList();
    }

    private record Candidato(Instant inicio, Instant fim) {
    }

    private List<LocalTime> candidatosDaJanela(JanelaHorario janela, int duracaoTotal, int granularidadeMinutos) {
        List<LocalTime> candidatos = new java.util.ArrayList<>();
        LocalTime candidato = janela.getHoraInicio();
        while (!candidato.plusMinutes(duracaoTotal).isAfter(janela.getHoraFim())) {
            candidatos.add(candidato);
            candidato = candidato.plusMinutes(granularidadeMinutos);
        }
        return candidatos;
    }

    private boolean existeJanelaQueComporta(Profissional profissional, int diaSemana, LocalTime inicioLocal,
            LocalTime fimLocal) {
        return janelaHorarioRepository.findByProfissionalAndDiaSemanaOrderByHoraInicioAsc(profissional, diaSemana)
                .stream()
                .anyMatch(j -> !inicioLocal.isBefore(j.getHoraInicio()) && !fimLocal.isAfter(j.getHoraFim()));
    }

    private boolean seSobrepoem(Instant inicioA, Instant fimA, Instant inicioB, Instant fimB) {
        return inicioA.isBefore(fimB) && inicioB.isBefore(fimA);
    }

    private void validarData(LocalDate data, Barbearia barbearia, ZoneId fuso) {
        LocalDate hoje = ZonedDateTime.now(fuso).toLocalDate();
        if (data.isBefore(hoje)) {
            throw new NegocioException("Nao e possivel consultar disponibilidade no passado.");
        }
        if (data.isAfter(hoje.plusDays(barbearia.getAntecedenciaMaximaAgendamentoDias()))) {
            throw new NegocioException("A data esta alem da antecedencia maxima permitida para agendamento ("
                    + barbearia.getAntecedenciaMaximaAgendamentoDias() + " dia(s)).");
        }
    }

    /** Resolve UUIDs de servico para entidades, garantindo que todos existem e estao ativos. */
    public List<Servico> resolverServicosAtivos(List<UUID> servicoUuids) {
        if (servicoUuids == null || servicoUuids.isEmpty()) {
            throw new NegocioException("Selecione ao menos um servico.");
        }
        List<Servico> servicos = servicoUuids.stream().distinct().map(uuid -> servicoRepository
                .findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Servico nao encontrado: " + uuid)))
                .toList();
        servicos.stream().filter(s -> !s.isAtivo()).findAny().ifPresent(s -> {
            throw new NegocioException("O servico '" + s.getNome() + "' esta inativo.");
        });
        return servicos;
    }

    /** Resolve um profissional por UUID, garantindo que esta ativo e realiza todos os servicos informados. */
    public Profissional resolverProfissionalCapaz(UUID profissionalUuid, List<Servico> servicos) {
        Profissional profissional = profissionalRepository.findByUuidPublico(profissionalUuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Profissional nao encontrado."));
        if (!profissional.isAtivo()) {
            throw new NegocioException("O profissional esta inativo.");
        }
        validarProfissionalRealizaServicos(profissional, servicos);
        return profissional;
    }

    /** Lista profissionais ativos capazes de realizar todos os servicos informados (ou um so, se especificado). */
    public List<Profissional> resolverProfissionaisCapazes(UUID profissionalUuidOpcional, List<Servico> servicos) {
        if (profissionalUuidOpcional != null) {
            return List.of(resolverProfissionalCapaz(profissionalUuidOpcional, servicos));
        }

        return profissionalRepository.findAll().stream()
                .filter(Profissional::isAtivo)
                .filter(profissional -> servicos.stream()
                        .allMatch(servico -> profissionalServicoRepository.existsByProfissionalAndServico(
                                profissional, servico)))
                .toList();
    }

    private void validarProfissionalRealizaServicos(Profissional profissional, List<Servico> servicos) {
        for (Servico servico : servicos) {
            if (!profissionalServicoRepository.existsByProfissionalAndServico(profissional, servico)) {
                throw new NegocioException(
                        "O profissional '" + profissional.getNome() + "' nao realiza o servico '"
                                + servico.getNome() + "'.");
            }
        }
    }

    Barbearia obterBarbearia() {
        return barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
    }
}
