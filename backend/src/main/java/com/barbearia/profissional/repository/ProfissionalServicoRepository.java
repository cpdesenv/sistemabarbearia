package com.barbearia.profissional.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.domain.ProfissionalServico;

public interface ProfissionalServicoRepository extends JpaRepository<ProfissionalServico, Long> {

    List<ProfissionalServico> findByProfissional(Profissional profissional);

    void deleteByProfissional(Profissional profissional);
}
