package com.barbearia.horario.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.barbearia.horario.domain.Bloqueio;
import com.barbearia.profissional.domain.Profissional;

public interface BloqueioRepository extends JpaRepository<Bloqueio, Long>, JpaSpecificationExecutor<Bloqueio> {

    Optional<Bloqueio> findByUuidPublico(UUID uuidPublico);

    /**
     * Bloqueios que se sobrepoem ao periodo informado: especificos do
     * profissional OU globais (profissional nulo — fecham a barbearia
     * inteira, ver {@link Bloqueio}).
     */
    @Query("""
            SELECT b FROM Bloqueio b
            WHERE (b.profissional = :profissional OR b.profissional IS NULL)
              AND b.inicio < :fim AND b.fim > :inicio
            """)
    List<Bloqueio> buscarSobrepondo(@Param("profissional") Profissional profissional,
            @Param("inicio") Instant inicio, @Param("fim") Instant fim);
}
