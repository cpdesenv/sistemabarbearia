package com.barbearia.horario.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.barbearia.horario.domain.Bloqueio;

public interface BloqueioRepository extends JpaRepository<Bloqueio, Long>, JpaSpecificationExecutor<Bloqueio> {

    Optional<Bloqueio> findByUuidPublico(UUID uuidPublico);
}
