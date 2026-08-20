package com.barbearia.horario.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.horario.domain.JanelaHorario;
import com.barbearia.profissional.domain.Profissional;

public interface JanelaHorarioRepository extends JpaRepository<JanelaHorario, Long> {

    List<JanelaHorario> findByProfissionalOrderByDiaSemanaAscHoraInicioAsc(Profissional profissional);

    void deleteByProfissional(Profissional profissional);
}
