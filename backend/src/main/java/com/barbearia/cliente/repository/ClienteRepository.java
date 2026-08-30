package com.barbearia.cliente.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.barbearia.cliente.domain.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {

    Optional<Cliente> findByUuidPublico(UUID uuidPublico);

    Optional<Cliente> findByTelefone(String telefone);

    long countByCriadoEmBetween(Instant inicio, Instant fim);
}
