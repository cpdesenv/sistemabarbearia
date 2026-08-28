package com.barbearia.mensageria.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.barbearia.mensageria.domain.Conversa;
import com.barbearia.mensageria.domain.ModoAtendimento;

public interface ConversaRepository extends JpaRepository<Conversa, Long> {

    Optional<Conversa> findByUuidPublico(UUID uuidPublico);

    Optional<Conversa> findByTelefoneE164(String telefoneE164);

    Page<Conversa> findAllByOrderByUltimaMensagemEmDesc(Pageable pageable);

    Page<Conversa> findAllByModoAtendimentoOrderByUltimaMensagemEmDesc(ModoAtendimento modoAtendimento,
            Pageable pageable);

    /** Kill switch (PRD, Fase 10): joga toda conversa em modo IA imediatamente para HUMANO. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Conversa c SET c.modoAtendimento = com.barbearia.mensageria.domain.ModoAtendimento.HUMANO, "
            + "c.motivoEscalonamento = :motivo WHERE c.modoAtendimento = com.barbearia.mensageria.domain.ModoAtendimento.IA")
    int escalarTodasParaHumano(@Param("motivo") String motivo);
}
