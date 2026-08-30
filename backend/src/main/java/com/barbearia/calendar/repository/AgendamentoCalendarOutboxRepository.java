package com.barbearia.calendar.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.calendar.domain.AgendamentoCalendarOutbox;
import com.barbearia.calendar.domain.StatusOutbox;

public interface AgendamentoCalendarOutboxRepository extends JpaRepository<AgendamentoCalendarOutbox, Long> {

    Optional<AgendamentoCalendarOutbox> findByAgendamentoAndStatus(Agendamento agendamento, StatusOutbox status);

    List<AgendamentoCalendarOutbox> findByStatusAndProximaTentativaEmLessThanEqualOrderByProximaTentativaEmAsc(
            StatusOutbox status, Instant limite);

    List<AgendamentoCalendarOutbox> findByStatusNot(StatusOutbox status);

    List<AgendamentoCalendarOutbox> findByStatusOrTentativasGreaterThanOrderByProximaTentativaEmAsc(
            StatusOutbox status, int tentativas);

    long countByStatus(StatusOutbox status);
}
