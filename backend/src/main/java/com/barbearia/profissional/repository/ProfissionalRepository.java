package com.barbearia.profissional.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.barbearia.profissional.domain.Profissional;

public interface ProfissionalRepository extends JpaRepository<Profissional, Long>,
        JpaSpecificationExecutor<Profissional> {

    Optional<Profissional> findByUuidPublico(UUID uuidPublico);
}
