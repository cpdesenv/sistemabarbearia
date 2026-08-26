package com.barbearia.assinatura.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.assinatura.domain.PlanoAssinatura;

public interface PlanoAssinaturaRepository extends JpaRepository<PlanoAssinatura, Long> {

    Optional<PlanoAssinatura> findByUuidPublico(UUID uuidPublico);

    List<PlanoAssinatura> findByAtivoOrderByNome(boolean ativo);

    List<PlanoAssinatura> findAllByOrderByNome();
}
