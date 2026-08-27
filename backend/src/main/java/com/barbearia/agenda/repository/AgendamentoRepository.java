package com.barbearia.agenda.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.profissional.domain.Profissional;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long>,
        JpaSpecificationExecutor<Agendamento> {

    Optional<Agendamento> findByUuidPublico(UUID uuidPublico);

    List<Agendamento> findByCliente_UuidPublicoOrderByInicioDesc(UUID clienteUuidPublico);

    /**
     * Agendamentos do profissional que ocupam a agenda (status diferente de
     * CANCELADO/NAO_COMPARECEU) e que se sobrepoem ao periodo informado.
     * Usado pelo motor de disponibilidade e pela validacao de criacao/edicao
     * — a garantia definitiva contra corrida de concorrencia continua sendo
     * a constraint de exclusao do banco.
     */
    @Query("""
            SELECT a FROM Agendamento a
            WHERE a.profissional = :profissional
              AND a.status NOT IN (com.barbearia.agenda.domain.StatusAgendamento.CANCELADO,
                                    com.barbearia.agenda.domain.StatusAgendamento.NAO_COMPARECEU)
              AND a.inicio < :fim AND a.fim > :inicio
              AND (:idIgnorar IS NULL OR a.id <> :idIgnorar)
            """)
    List<Agendamento> buscarConflitantes(@Param("profissional") Profissional profissional,
            @Param("inicio") Instant inicio, @Param("fim") Instant fim, @Param("idIgnorar") Long idIgnorar);
}
