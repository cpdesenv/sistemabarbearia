package com.barbearia.mensageria.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.mensageria.domain.MensagemEnvioOutbox;
import com.barbearia.mensageria.domain.StatusEnvioOutbox;

public interface MensagemEnvioOutboxRepository extends JpaRepository<MensagemEnvioOutbox, Long> {

    List<MensagemEnvioOutbox> findByStatusAndProximaTentativaEmLessThanEqualOrderByProximaTentativaEmAsc(
            StatusEnvioOutbox status, Instant limite);
}
