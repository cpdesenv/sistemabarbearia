package com.barbearia.profissional.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.domain.ProfissionalServico;
import com.barbearia.servico.domain.Servico;

public interface ProfissionalServicoRepository extends JpaRepository<ProfissionalServico, Long> {

    List<ProfissionalServico> findByProfissional(Profissional profissional);

    boolean existsByProfissionalAndServico(Profissional profissional, Servico servico);

    void deleteByProfissional(Profissional profissional);
}
