package com.barbearia.horario.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.horario.domain.JanelaHorario;
import com.barbearia.horario.dto.JanelaHorarioDto;
import com.barbearia.horario.dto.SalvarJanelaHorarioRequest;
import com.barbearia.horario.repository.JanelaHorarioRepository;
import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.repository.ProfissionalRepository;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

@Service
@RequiredArgsConstructor
public class GradeHorariaService {

    private final ProfissionalRepository profissionalRepository;
    private final JanelaHorarioRepository janelaHorarioRepository;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<JanelaHorarioDto> listar(UUID profissionalUuid) {
        Profissional profissional = buscarProfissional(profissionalUuid);
        return janelaHorarioRepository.findByProfissionalOrderByDiaSemanaAscHoraInicioAsc(profissional).stream()
                .map(this::paraDto)
                .toList();
    }

    @Transactional
    public List<JanelaHorarioDto> sincronizar(UUID profissionalUuid, List<SalvarJanelaHorarioRequest> requisicao,
            Long usuarioId, HttpServletRequest httpRequest) {
        Profissional profissional = buscarProfissional(profissionalUuid);
        validar(requisicao);

        janelaHorarioRepository.deleteByProfissional(profissional);

        List<JanelaHorario> janelas = requisicao.stream()
                .map(item -> new JanelaHorario(profissional, item.diaSemana(), item.horaInicio(), item.horaFim()))
                .toList();
        janelaHorarioRepository.saveAll(janelas);

        auditoriaService.registrar(usuarioId, "GRADE_HORARIA_ATUALIZADA", "profissional", profissional.getId(),
                "Grade de horarios do profissional '" + profissional.getNome() + "' atualizada ("
                        + janelas.size() + " janela(s))",
                httpRequest);

        return janelas.stream().map(this::paraDto).toList();
    }

    private void validar(List<SalvarJanelaHorarioRequest> requisicao) {
        for (int i = 0; i < requisicao.size(); i++) {
            SalvarJanelaHorarioRequest atual = requisicao.get(i);
            if (!atual.horaFim().isAfter(atual.horaInicio())) {
                throw new NegocioException("O horario final deve ser depois do horario inicial.");
            }
            for (int j = i + 1; j < requisicao.size(); j++) {
                SalvarJanelaHorarioRequest outra = requisicao.get(j);
                if (atual.diaSemana().equals(outra.diaSemana()) && seSobrepoem(atual, outra)) {
                    throw new NegocioException("Ha janelas de horario sobrepostas no mesmo dia.");
                }
            }
        }
    }

    private boolean seSobrepoem(SalvarJanelaHorarioRequest a, SalvarJanelaHorarioRequest b) {
        return a.horaInicio().isBefore(b.horaFim()) && b.horaInicio().isBefore(a.horaFim());
    }

    private JanelaHorarioDto paraDto(JanelaHorario janela) {
        return new JanelaHorarioDto(janela.getDiaSemana(), janela.getHoraInicio(), janela.getHoraFim());
    }

    private Profissional buscarProfissional(UUID uuid) {
        return profissionalRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Profissional nao encontrado."));
    }
}
