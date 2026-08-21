package com.barbearia.financeiro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.financeiro.domain.Comanda;
import com.barbearia.financeiro.domain.StatusComanda;

public interface ComandaRepository extends JpaRepository<Comanda, Long> {

    Optional<Comanda> findByUuidPublico(UUID uuidPublico);

    Optional<Comanda> findByAgendamento_UuidPublicoAndStatus(UUID agendamentoUuidPublico, StatusComanda status);

    List<Comanda> findByAgendamento_UuidPublicoOrderByCriadoEmDesc(UUID agendamentoUuidPublico);

    List<Comanda> findByStatusAndFechadaEmBetween(StatusComanda status, Instant inicio, Instant fim);
}
