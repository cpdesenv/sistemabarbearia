package com.barbearia.servico.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.barbearia.servico.domain.Servico;

public interface ServicoRepository extends JpaRepository<Servico, Long>, JpaSpecificationExecutor<Servico> {

    Optional<Servico> findByUuidPublico(UUID uuidPublico);

    List<Servico> findByAtivoTrueOrderByNomeAsc();
}
