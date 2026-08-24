package com.barbearia.fiscal.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.fiscal.domain.Comprovante;

public interface ComprovanteRepository extends JpaRepository<Comprovante, Long> {

    Optional<Comprovante> findByUuidPublico(UUID uuidPublico);

    Optional<Comprovante> findByComanda_UuidPublico(UUID comandaUuidPublico);
}
